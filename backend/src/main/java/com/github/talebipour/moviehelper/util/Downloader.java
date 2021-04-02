package com.github.talebipour.moviehelper.util;

import com.github.talebipour.moviehelper.exception.InvalidInputException;
import com.github.talebipour.moviehelper.model.DownloadStatus;
import com.github.talebipour.moviehelper.model.DownloadStatus.Status;
import com.github.talebipour.moviehelper.model.FileModel;
import com.github.talebipour.moviehelper.model.FileModel.FileType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class Downloader {

    private static final Logger logger = LoggerFactory.getLogger(Downloader.class);

    private static final String DOWNLOAD_USER_AGENT = "Mozilla/5.0 (Linux; Android 8.0.0; " +
            "SM-G960F Build/R16NW) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.84 Mobile Safari/537.36";
    private static final String FILENAME_DIRECTIVE = "filename=";

    private final HttpClient client;

    private final Map<String, DownloadStatus> statusMap = new ConcurrentHashMap<>();

    private final int bufferSize;
    private final FileUtil fileUtil;

    public Downloader(@Value("${downloader.buffer.size}") int bufferSize, FileUtil fileUtil) {
        this.bufferSize = bufferSize;
        this.fileUtil = fileUtil;
        client = HttpClient.newBuilder().build();
    }

    public Set<String> downloadSubtitle(String url, Path path) throws IOException, InterruptedException {
        logger.info("Downloading subtitle from {} into {}", url, path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .header(HttpHeaders.USER_AGENT, DOWNLOAD_USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, BodyHandlers.ofByteArray());
        if (!isSuccessful(response.statusCode())) {
            throw new InvalidInputException();
        }
        Set<String> files = saveSubtitles(response.body(), path);
        logger.info("Download subtitles: {} succeed.", files);
        return files;
    }

    private boolean isSuccessful(int statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status != null && status.is2xxSuccessful();
    }

    private Set<String> saveSubtitles(byte[] zipContent, Path path) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(zipContent);
        HashSet<String> files = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryName = Paths.get(entry.getName());
                if (entry.getName().toLowerCase().endsWith(".srt")) {
                    Files.write(path.resolve(entryName.getFileName()), IOUtils.toByteArray(zis));
                    files.add(entryName.getFileName().toString());
                }
                zis.closeEntry();
            }
        }
        return files;
    }

    public DownloadStatus asyncDownload(String url, String path) throws IOException, InterruptedException {
        if (statusMap.containsKey(url) && statusMap.get(url).getStatus() == Status.IN_PROGRESS) {
            throw new InvalidInputException("Download is already in progress");
        }
        DownloadStatus status = new DownloadStatus();
        status.setUrl(url);
        status.setStatus(Status.IN_PROGRESS);
        FileModel file = new FileModel();
        file.setType(FileType.REGULAR);
        file.setPath(path);
        status.setFile(file);
        statusMap.put(url, status);
        findFileInfo(status);
        startDownload(status);
        return status;
    }

    private void findFileInfo(DownloadStatus status) throws IOException, InterruptedException {
        URI uri = URI.create(status.getUrl());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header(HttpHeaders.RANGE, "bytes=0-1")
                .GET()
                .build();
        HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        if (!isSuccessful(response.statusCode())) {
            throw new InvalidInputException();
        }

        status.getFile().setName(findFilename(response.headers().allValues(HttpHeaders.CONTENT_DISPOSITION))
                             .orElse(uri.getPath().substring(1)));

        status.getFile().setSize(findFileSize(response));
//        status.setRangeSupported(response.statusCode() == HttpStatus.PARTIAL_CONTENT.value());
        status.setRangeSupported(false);
    }

    private long findFileSize(HttpResponse<Void> response) {
        if (response.statusCode() == HttpStatus.PARTIAL_CONTENT.value()) {
            Optional<String> contentRange = response.headers().firstValue(HttpHeaders.CONTENT_RANGE);
            if (!contentRange.isPresent()) {
                throw new InvalidInputException("Content-Range header not found.");
            }
            int slashPos = contentRange.get().indexOf("/");
            if (slashPos >= 0) {
                return Long.parseLong(contentRange.get().substring(slashPos + 1));
            }
            return -1;
        } else {
            return response.headers().firstValueAsLong(HttpHeaders.CONTENT_LENGTH).orElse(-1);
        }
    }

    private Optional<String> findFilename(List<String> contentDispositions) {
        for (String disposition : contentDispositions) {
            int index = disposition.indexOf(FILENAME_DIRECTIVE);
            if (index >= 0) {
                return Optional.of(disposition.substring(index + FILENAME_DIRECTIVE.length()));
            }
        }
        return Optional.empty();
    }

    private void startDownload(DownloadStatus status) {
        if (status.isRangeSupported()) {
            //TODO: Complete
        } else {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(status.getUrl()))
                    .GET()
                    .build();
            CompletableFuture<HttpResponse<Path>> future = client.sendAsync(request,
                    BodyHandlers.ofFile(downloadPath(status.getFile())));
            future.whenComplete((pathHttpResponse, throwable) -> {
                if (throwable == null) {
                    status.setStatus(Status.COMPLETED);
                } else {
                    logger.error("Downloading {} failed. {}", status.getUrl(), throwable.getMessage());
                    status.setStatus(Status.FAILED);
                    status.setMessage(throwable.getMessage());
                }
            });
        }
    }

    private Path downloadPath(FileModel file) {
        Path path;
        int i = 1;
        do {
            path = fileUtil.resolvePath(file.getPath()).resolve(file.getName() + (i == 1 ? "" : "." + i));
            i++;
        } while (Files.exists(path));
        return path;
    }

    public Collection<DownloadStatus> getDownloadsStatuses() {
        return statusMap.values();
    }

    public Optional<DownloadStatus> getDownloadStatus(String url) {
        return Optional.ofNullable(statusMap.get(url));
    }
}
