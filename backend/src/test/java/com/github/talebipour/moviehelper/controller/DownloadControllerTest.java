package com.github.talebipour.moviehelper.controller;

import static com.github.talebipour.moviehelper.controller.FileProviderController.MOVIE_FILE_SIZE;
import static com.github.talebipour.moviehelper.controller.FileProviderController.SUBTITLE_1_CONTENT;
import static com.github.talebipour.moviehelper.controller.FileProviderController.SUBTITLE_1_NAME;
import static com.github.talebipour.moviehelper.controller.FileProviderController.SUBTITLE_2_CONTENT;
import static com.github.talebipour.moviehelper.controller.FileProviderController.SUBTITLE_2_NAME;
import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.talebipour.moviehelper.model.DownloadStatus;
import com.github.talebipour.moviehelper.model.DownloadStatus.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DownloadControllerTest {

    @TempDir
    static Path rootDir;

    private TestRestTemplate restTemplate;

    @Autowired
    public DownloadControllerTest(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("directory.path", () -> rootDir.toAbsolutePath().toString());
    }

    @Test
    public void testDownloadSubtitle() throws IOException {
        ResponseEntity<String> entity =
                restTemplate.getForEntity("/download/subtitle?path=&url=" + restTemplate.getRootUri() +
                                          "/sample-subtitle.zip", String.class);
        assertTrue(entity.getStatusCode().is2xxSuccessful());
        Set<String> files = Arrays.stream(entity.getBody().split("\n")).collect(Collectors.toSet());
        assertEquals(new HashSet<>(asList(SUBTITLE_1_NAME, SUBTITLE_2_NAME)), files);
        assertEquals(SUBTITLE_1_CONTENT, new String(Files.readAllBytes(rootDir.resolve(SUBTITLE_1_NAME))));
        assertEquals(SUBTITLE_2_CONTENT, new String(Files.readAllBytes(rootDir.resolve(SUBTITLE_2_NAME))));
    }

    @Test
    public void testDownloadFile() throws IOException {
        String filename = "movie.mkv";
        String url = restTemplate.getRootUri() + "/" + filename;
        ResponseEntity<DownloadStatus> entity = restTemplate.postForEntity("/download/file?path=&url=" + url, null,
                                                                          DownloadStatus.class);
        assertTrue(entity.getStatusCode().is2xxSuccessful());
        DownloadStatus status = entity.getBody();
        assertNotNull(status);
        assertEquals(url, status.getUrl());
        assertEquals(filename, status.getFile().getName());
        assertEquals(MOVIE_FILE_SIZE, status.getFile().getSize());

        waitAtMost(1, TimeUnit.MINUTES).dontCatchUncaughtExceptions().untilAsserted(() -> {
            ResponseEntity<List<DownloadStatus>> lastStatusEntity = getLastStatus(url);
            assertEquals(1, lastStatusEntity.getBody().size());
            DownloadStatus lastStatus = lastStatusEntity.getBody().get(0);
            assertEquals(Status.COMPLETED, lastStatus.getStatus());
            assertEquals(100, lastStatus.getProgressPercent());
        });
        Path downloadedFilePath = rootDir.resolve(filename);
        assertTrue(Files.exists(downloadedFilePath));
        assertEquals(MOVIE_FILE_SIZE, Files.size(downloadedFilePath));
        assertNull(status.getMessage());
        Files.delete(downloadedFilePath);
    }

    private ResponseEntity<List<DownloadStatus>> getLastStatus(String url) {
        ResponseEntity<List<DownloadStatus>> lastStatusEntity =
                restTemplate.exchange("/download/status?url=" + url, HttpMethod.GET, HttpEntity.EMPTY,
                                      new ParameterizedTypeReference<>() {});
        assertTrue(lastStatusEntity.getStatusCode().is2xxSuccessful());
        assertNotNull(lastStatusEntity.getBody());
        return lastStatusEntity;
    }

}