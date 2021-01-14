package com.github.talebipour.moviehelper;

import static java.util.Arrays.asList;

import com.github.talebipour.moviehelper.FileModel.FileType;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestTemplate;

@Controller
@CrossOrigin
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("(.*)\\.(.*)$");
    private static final Pattern SUBTITLE_PATTERN = Pattern.compile(".*\\.srt(.\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> MOVIE_EXTENSIONS = new HashSet<>(asList("mkv", "mp4"));
    private static final String SUBTITLE_DOWNLOAD_USER_AGENT = "Mozilla/5.0 (Linux; Android 8.0.0; " +
            "SM-G960F Build/R16NW) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.84 Mobile Safari/537.36";
    private final Path rootDir;

    private static final Comparator<FileModel> FILE_MODEL_COMPARATOR = ((Comparator<FileModel>) (o1, o2) -> {
        if (o1.getType() == o2.getType()) {
            return 0;
        }
        return (o1.getType() == FileType.DIRECTORY) ? -1 : 1;
    }).thenComparing(FileModel::getName);

    public FileController(@Value("${directory.path}") String rootDir) {
        File dir = new File(rootDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + rootDir);
        }
        this.rootDir = dir.toPath();
    }

    @GetMapping(value = "/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody
    List<FileModel> listFilesAtPath(@RequestParam(value = "path", required = false) String path) throws IOException {
        logger.info("Listing files in path {}", path);
        try {
            Path target = resolvePath(path);
            return Files.walk(target, 1)
                    .filter(subPath -> !subPath.equals(target))
                    .map(this::toFileModel)
                    .sorted(FILE_MODEL_COMPARATOR)
                    .collect(Collectors.toList());
        } catch (NoSuchFileException e) {
            throw new PathNotFoundException();
        }
    }

    @PostMapping(value = "/set-subtitle", produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody
    String setSubtitle(@RequestParam String subtitle, @RequestParam String movie) throws IOException {
        Path subtitlePath = resolvePath(subtitle);
        if (!SUBTITLE_PATTERN.matcher(subtitlePath.getFileName().toString()).matches()) {
            throw new InvalidInputException();
        }
        Path moviePath = resolvePath(movie);
        if (!MOVIE_EXTENSIONS.contains(extension(moviePath.getFileName().toString().toLowerCase()))) {
            throw new InvalidInputException();
        }

        String filename = filenameWithoutExtension(moviePath.getFileName().toString()) + ".srt";
        Path target = moviePath.resolveSibling(filename);
        if (Files.exists(target)) {
            Path backupFile;
            int i = 1;
            do {
                backupFile = target.resolveSibling(target.getFileName() + "." + i);
                i++;
            } while (Files.exists(backupFile));
            logger.info("Take backup from {} file to {}", target, backupFile);
            Files.move(target, backupFile);
        }
        logger.info("Renaming {} file to {}", subtitlePath, target);
        Files.move(subtitlePath, target);
        return subtitlePath.getFileName().toString();
    }

    @PostMapping(value = "/reload-minidlna")
    public void reloadMiniDlna() throws IOException {
        logger.info("Reloading MiniDLNA...");
        Process process = new ProcessBuilder().command("systemctl", "force-reload", "minidlna").start();
        try {
            if (process.waitFor(15, TimeUnit.SECONDS)) {
                throw new InternalServerError("MiniDLNA force-reload timed out.");
            }
            if (process.exitValue() != 0) {
                StringWriter error = new StringWriter();
                IOUtils.copy(process.getErrorStream(), error);
                logger.error("Reload MiniDLNA failed: {}", error);
                throw new InternalServerError("MiniDLNA force-reload failed, exit code=" + process.exitValue());
            }
            logger.info("MiniDLNA reloaded.");
        } catch (InterruptedException e) {
            throw new InternalServerError("Interrupted while waiting for MiniDLNA force-reload", e);
        }
    }

    @GetMapping("/download-subtitle")
    public ResponseEntity<String> downloadSubtitle(@RequestParam String url,
            @RequestParam(required = false, defaultValue = ".") String path) throws IOException {
        logger.info("Downloading subtitle from {} into {}", url, path);
        RestTemplate template = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, SUBTITLE_DOWNLOAD_USER_AGENT);
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> entity = template.exchange(url, HttpMethod.GET, requestEntity, byte[].class);
        
        if (entity.getStatusCode().is2xxSuccessful()) {
            try {
                Set<String> files = saveSubtitles(entity.getBody(), path);
                logger.info("Download subtitles: {} succeed.", files);
                return ResponseEntity.ok(String.join("\n", files));
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return ResponseEntity.badRequest().body("Download failed, status code=" + entity.getStatusCode());
    }

    private Set<String> saveSubtitles(byte[] zipContent, String path) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(zipContent);
        Path targetPath = rootDir.resolve(path);
        HashSet<String> files = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryName = Paths.get(entry.getName());
                if (entry.getName().toLowerCase().endsWith(".srt")) {
                    Files.write(targetPath.resolve(entryName.getFileName()), IOUtils.toByteArray(zis));
                    files.add(entryName.getFileName().toString());
                }
                zis.closeEntry();
            }
        }
        return files;
    }

    static String extension(String filename) {
        Matcher matcher = EXTENSION_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new InvalidInputException();
        }
        return matcher.group(2);
    }

    static String filenameWithoutExtension(String filename) {
        Matcher matcher = EXTENSION_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new InvalidInputException();
        }
        return matcher.group(1);
    }

    private Path resolvePath(String path) {
        if (path != null && path.contains("..")) {
            throw new InvalidInputException();
        }
        return path == null || path.isEmpty() ? rootDir : rootDir.resolve(path);
    }

    private FileModel toFileModel(Path path) {
        FileModel model = new FileModel();
        model.setName(path.getFileName().toString());
        model.setPath(rootDir.relativize(path).toString());
        model.setType(Files.isDirectory(path) ? FileType.DIRECTORY : FileType.REGULAR);
        try {
            model.setSize(Files.size(path));
        } catch (IOException e) {
            logger.error("Getting file {} size failed.", path, e);
        }
        return model;
    }


    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidInputException extends RuntimeException {
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class PathNotFoundException extends RuntimeException {
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class InternalServerError extends RuntimeException {
        public InternalServerError(String message) {
            super(message);
        }

        public InternalServerError(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
