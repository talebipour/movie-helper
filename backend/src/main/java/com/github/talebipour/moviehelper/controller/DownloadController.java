package com.github.talebipour.moviehelper.controller;

import com.github.talebipour.moviehelper.exception.InternalServerError;
import com.github.talebipour.moviehelper.model.DownloadStatus;
import com.github.talebipour.moviehelper.util.Downloader;
import com.github.talebipour.moviehelper.util.FileUtil;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/file")
    public DownloadStatus downloadFile(@RequestParam String url,
                                       @RequestParam(required = false, defaultValue = ".") String path) {
        try {
            return downloader.asyncDownload(url, path);
        } catch (IOException | InterruptedException e) {
            throw new InternalServerError(e.getMessage());
        }
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<DownloadStatus> getDownloadStatus(@RequestParam(required = false) String url) {
        if (url == null || url.isEmpty()) {
            return downloader.getDownloadsStatuses();
        }
        Optional<DownloadStatus> status = downloader.getDownloadStatus(url);
        return status.map(List::of).orElse(Collections.emptyList());
    }
}