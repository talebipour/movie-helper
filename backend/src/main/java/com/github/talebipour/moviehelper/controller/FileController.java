package com.github.talebipour.moviehelper.controller;

import static java.util.Arrays.asList;

import com.github.talebipour.moviehelper.exception.InternalServerError;
import com.github.talebipour.moviehelper.exception.InvalidInputException;
import com.github.talebipour.moviehelper.exception.PathNotFoundException;
import com.github.talebipour.moviehelper.model.FileModel;
import com.github.talebipour.moviehelper.model.FileModel.FileType;
import com.github.talebipour.moviehelper.util.FileUtil;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private static final Pattern SUBTITLE_PATTERN = Pattern.compile(".*\\.srt(.\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> MOVIE_EXTENSIONS = new HashSet<>(asList("mkv", "mp4"));

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

    @PostMapping(value = "/set-subtitle/single", produces = MediaType.TEXT_PLAIN_VALUE)
    public @ResponseBody
    String setSubtitleSingle(@RequestParam String subtitle, @RequestParam String movie) throws IOException {
        Path subtitlePath = fileUtil.resolvePath(subtitle);
        if (!SUBTITLE_PATTERN.matcher(subtitlePath.getFileName().toString()).matches()) {
            throw new InvalidInputException();
        }
        Path moviePath = fileUtil.resolvePath(movie);
        if (!MOVIE_EXTENSIONS.contains(FileUtil.extension(moviePath.getFileName().toString().toLowerCase()))) {
            throw new InvalidInputException();
        }

        return doSetSubtitle(subtitlePath, moviePath);
    }

    @PostMapping(value = "/set-subtitle/bulk", produces = MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody
    List<List<FileModel>> setSubtitleBulk(@RequestParam(value = "path", required = false) String path,
                                 @RequestParam String movieRegex, @RequestParam String subtitleRegex)
            throws IOException {
        List<List<FileModel>> result = new ArrayList<>();
        Pattern moviePattern = Pattern.compile(movieRegex, Pattern.CASE_INSENSITIVE);
        try {
            Path target = fileUtil.resolvePath(path);
            Files.list(target).forEach(moviePath -> {
                Matcher matcher = moviePattern.matcher(moviePath.getFileName().toString());
                String replacedSubtitleRegex = subtitleRegex;
                if (matcher.matches()) {
                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        replacedSubtitleRegex = replacedSubtitleRegex.replace("$" + i, Pattern.quote(matcher.group(i)));
                    }
                }
                Predicate<String> subtitlePredicate = Pattern.compile(replacedSubtitleRegex, Pattern.CASE_INSENSITIVE)
                        .asMatchPredicate();
                List<Path> subtitles;
                try {
                    subtitles = Files.list(target)
                            .filter(filePath -> subtitlePredicate.test(filePath.getFileName().toString()))
                            .collect(Collectors.toList());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (subtitles.size() > 1) {
                    throw new InvalidInputException("Subtitle regex result is not unique.");
                }
                if (!subtitles.isEmpty()) {
                    try {
                        doSetSubtitle(subtitles.get(0), moviePath);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    result.add(List.of(toFileModel(moviePath), toFileModel(subtitles.get(0))));
                }
            });
        } catch (NoSuchFileException e) {
            throw new PathNotFoundException();
        }
        return result;
    }


    private String doSetSubtitle(Path subtitlePath, Path moviePath) throws IOException {
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
