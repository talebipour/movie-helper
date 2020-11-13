package com.github.talebipour.moviehelper;

import static java.util.Arrays.asList;

import com.github.talebipour.moviehelper.FileModel.FileType;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@CrossOrigin
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("(.*)\\.(.*)$");
    private static final Pattern SUBTITLE_PATTERN = Pattern.compile(".*\\.srt(.\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> MOVIE_EXTENSIONS = new HashSet<>(asList("mkv", "mp4"));
    private final Path rootDir;

    private static final Comparator<FileModel> FILE_MODEL_COMPARATOR = new Comparator<FileModel>() {
        @Override
        public int compare(FileModel o1, FileModel o2) {
            if (o1.getType() == o2.getType()) {
                return 0;
            }
            return (o1.getType() == FileType.DIRECTORY) ? -1 : 1;
        }
    }.thenComparing(FileModel::getName);

   public FileController(@Value("${directory.path}") String rootDir) {
        File dir = new File(rootDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + rootDir);
        }
        this.rootDir = dir.toPath();
    }

    @GetMapping(value = {"/files/{path}", "/files/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody
    List<FileModel> listFilesAtPath(@PathVariable(value = "path", required = false) String path) throws IOException {
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
