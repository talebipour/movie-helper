package com.github.talebipour.moviehelper.controller;

import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_1_CONTENT;
import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_1_NAME;
import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_2_CONTENT;
import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_2_NAME;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

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

}