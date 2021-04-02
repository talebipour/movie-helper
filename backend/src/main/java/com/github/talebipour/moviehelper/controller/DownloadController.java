package com.github.talebipour.moviehelper.controller;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@CrossOrigin
@RequestMapping(path = "/download")
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    private static final String DOWNLOAD_USER_AGENT = "Mozilla/5.0 (Linux; Android 8.0.0; " +
            "SM-G960F Build/R16NW) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.84 Mobile Safari/537.36";
    private final FileUtil fileUtil;

    @Autowired
    public DownloadController(FileUtil fileUtil) {
        this.fileUtil = fileUtil;
    }


    @GetMapping("/subtitle")
    public ResponseEntity<String> downloadSubtitle(@RequestParam String url,
                                                   @RequestParam(required = false, defaultValue = ".") String path) {
        logger.info("Downloading subtitle from {} into {}", url, path);
        RestTemplate template = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, DOWNLOAD_USER_AGENT);
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
}
