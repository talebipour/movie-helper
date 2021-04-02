package com.github.talebipour.moviehelper.controller;

import com.github.talebipour.moviehelper.exception.InternalServerError;
import com.github.talebipour.moviehelper.util.Downloader;
import com.github.talebipour.moviehelper.util.FileUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@RequestMapping(path = "/download")
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    private final FileUtil fileUtil;
    private final Downloader downloader;

    @Autowired
    public DownloadController(FileUtil fileUtil, Downloader downloader) {
        this.fileUtil = fileUtil;
        this.downloader = downloader;
    }


    @GetMapping("/subtitle")
    public String downloadSubtitle(@RequestParam String url,
            @RequestParam(required = false, defaultValue = ".") String path) {
        try {
            Set<String> files = downloader.downloadSubtitle(url, fileUtil.resolvePath(path));
            return String.join("\n", files);
        } catch (IOException | InterruptedException e) {
            throw new InternalServerError(e.getMessage());
        }
    }
}
