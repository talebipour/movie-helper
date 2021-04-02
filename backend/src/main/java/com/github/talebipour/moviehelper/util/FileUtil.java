package com.github.talebipour.moviehelper.util;

import com.github.talebipour.moviehelper.exception.InvalidInputException;
import java.io.File;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileUtil {
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("(.*)\\.(.*)$");

    private final Path rootDir;

    public FileUtil(@Value("${directory.path}") String rootDir) {
        File dir = new File(rootDir);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + rootDir);
        }
        this.rootDir = dir.toPath();
    }

    public Path getRootDir() {
        return rootDir;
    }

    public static String extension(String filename) {
        Matcher matcher = EXTENSION_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new InvalidInputException();
        }
        return matcher.group(2);
    }

    public static String filenameWithoutExtension(String filename) {
        Matcher matcher = EXTENSION_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            throw new InvalidInputException();
        }
        return matcher.group(1);
    }

    public Path resolvePath(String path) {
        if (path != null && path.contains("..")) {
            throw new InvalidInputException();
        }
        return path == null || path.isEmpty() ? rootDir : rootDir.resolve(path);
    }
}
