package com.github.talebipour.moviehelper.controller;

import static java.util.Arrays.asList;

import com.github.talebipour.moviehelper.model.FileModel;
import com.github.talebipour.moviehelper.model.FileModel.FileType;
import com.github.talebipour.moviehelper.exception.InternalServerError;
import com.github.talebipour.moviehelper.exception.InvalidInputException;
import com.github.talebipour.moviehelper.exception.PathNotFoundException;
import com.github.talebipour.moviehelper.util.FileUtil;
import java.io.ByteArrayInputStream;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@CrossOrigin
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private static final Pattern SUBTITLE_PATTERN = Pattern.compile(".*\\.srt(.\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> MOVIE_EXTENSIONS = new HashSet<>(asList("mkv", "mp4"));
    private static final String SUBTITLE_DOWNLOAD_USER_AGENT = "Mozilla/5.0 (Linux; Android 8.0.0; " +
            "SM-G960F Build/R16NW) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.84 Mobile Safari/537.36";

    private final FileUtil fileUtil;

    private static final Comparator<FileModel> FILE_MODEL_COMPARATOR = ((Comparator<FileModel>) (o1, o2) -> {
        if (o1.getType() == o2.getType()) {
            return 0;
        }
        return (o1.getType() == FileType.DIRECTORY) ? -1 : 1;
    }).thenComparing(FileModel::getName);

    @Autowired
    public FileController(FileUtil fileUtil) {
        this.fileUtil = fileUtil;
    }

    @GetMapping(value = "/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody
    List<FileModel> listFilesAtPath(@RequestParam(value = "path", required = false) String path) throws IOException {
        logger.info("Listing files in path {}", path);
        try {
            Path target = fileUtil.resolvePath(path);
            return Files.walk(target, 1)
                    .filter(subPath -> !subPath.equals(target))
                    .map(this::toFileModel)
                    .sorted(FILE_MODEL_COMPARATOR)
                    .collect(Collectors.toList());
        } catch (NoSuchFileException e) {
            throw new PathNotFoundException();
        }
    }


    @DeleteMapping("/files")
    public void deleteFiles(@RequestParam(value = "path", required = false) List<String> paths) throws IOException {
        logger.info("Removing {} files.", paths);
        try {
          for (String path : paths) {
            Path target = fileUtil.resolvePath(path);
            Files.delete(target);
            logger.info("{} file removed.", path);
          }
        } catch (NoSuchFileException e) {
            throw new PathNotFoundException();
        }
    }

    @PostMapping(value = "/set-subtitle", produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody
    String setSubtitle(@RequestParam String subtitle, @RequestParam String movie) throws IOException {
        Path subtitlePath = fileUtil.resolvePath(subtitle);
        if (!SUBTITLE_PATTERN.matcher(subtitlePath.getFileName().toString()).matches()) {
            throw new InvalidInputException();
        }
        Path moviePath = fileUtil.resolvePath(movie);
        if (!MOVIE_EXTENSIONS.contains(FileUtil.extension(moviePath.getFileName().toString().toLowerCase()))) {
            throw new InvalidInputException();
        }

        String filename = FileUtil.filenameWithoutExtension(moviePath.getFileName().toString()) + ".srt";
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
        Path targetPath = fileUtil.resolvePath(path);
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

    private FileModel toFileModel(Path path) {
        FileModel model = new FileModel();
        model.setName(path.getFileName().toString());
        model.setPath(fileUtil.getRootDir().relativize(path).toString());
        model.setType(Files.isDirectory(path) ? FileType.DIRECTORY : FileType.REGULAR);
        try {
            model.setSize(Files.size(path));
        } catch (IOException e) {
            logger.error("Getting file {} size failed.", path, e);
        }
        return model;
    }


}