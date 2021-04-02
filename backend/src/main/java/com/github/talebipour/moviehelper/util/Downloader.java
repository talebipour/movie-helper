package com.github.talebipour.moviehelper.util;

import com.github.talebipour.moviehelper.exception.InvalidInputException;
import com.github.talebipour.moviehelper.model.FileModel;
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
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class Downloader {

    private static final Logger logger = LoggerFactory.getLogger(Downloader.class);

    private static final String DOWNLOAD_USER_AGENT = "Mozilla/5.0 (Linux; Android 8.0.0; " +
            "SM-G960F Build/R16NW) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.84 Mobile Safari/537.36";

    private final HttpClient client;

    public Downloader() {
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
        HttpStatus status = HttpStatus.resolve(response.statusCode());
        if (status == null || !status.is2xxSuccessful()) {
            throw new InvalidInputException();
        }
        Set<String> files = saveSubtitles(response.body(), path);
        logger.info("Download subtitles: {} succeed.", files);
        return files;
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
}
