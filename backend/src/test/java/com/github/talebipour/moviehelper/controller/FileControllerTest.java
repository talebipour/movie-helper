package com.github.talebipour.moviehelper.controller;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.talebipour.moviehelper.model.FileModel;
import com.github.talebipour.moviehelper.model.FileModel.FileType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FileControllerTest {

    @TempDir
    static Path rootDir;

    private TestRestTemplate restTemplate;

    @Autowired
    public FileControllerTest(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("directory.path", () -> rootDir.toAbsolutePath().toString());
    }

    @BeforeEach
    void setup() throws IOException {
        Files.list(rootDir).filter(path -> !path.equals(rootDir)).forEach(path -> {
            try {
                FileSystemUtils.deleteRecursively(path);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void testListFiles() throws IOException {
        List<FileModel> files = listFiles("");
        assertEquals(Collections.emptyList(), files);

        Path dir1 = rootDir.resolve("dir1");
        Files.createDirectories(dir1);
        Files.createFile(rootDir.resolve("file1"));
        Files.createFile(dir1.resolve("nested1"));

        files = listFiles("");
        assertEquals(asList(new FileModel("dir1", "dir1", FileType.DIRECTORY, 0),
                            new FileModel("file1", "file1", FileType.REGULAR, 0)), files);

        files = listFiles("dir1");
        assertEquals(singletonList(new FileModel("nested1", "dir1/nested1", FileType.REGULAR, 0)), files);

        assertEquals(HttpStatus.NOT_FOUND, getHttpStatus("notExists"));
    }


    @Test
    public void testListFileJail() {
        assertEquals(HttpStatus.BAD_REQUEST, getHttpStatus(".."));
    }

    @Test
    public void testSetSubtitleSingle() throws IOException {
        Files.createFile(rootDir.resolve("movie.mkv"));
        Files.createFile(rootDir.resolve("subtitle.srt"));
        String url = "/set-subtitle/single?subtitle=subtitle.srt&movie=movie.mkv";
        Path subtitlePath = rootDir.resolve("movie.srt");
        assertFalse(Files.exists(subtitlePath));
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Files.isRegularFile(subtitlePath));

        Path backupSubtitlePath = rootDir.resolve("movie.srt.1");
        assertFalse(Files.exists(backupSubtitlePath));
        String subContent = "subContent";
        Files.write(rootDir.resolve("subtitle.srt"), subContent.getBytes());
        response = restTemplate.postForEntity(url, null, String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(Files.isRegularFile(backupSubtitlePath));
        assertEquals(subContent, new String(Files.readAllBytes(subtitlePath)));
    }

    @Test
    public void testSetSubtitleBulk() throws IOException {
        Path seriesDir = rootDir.resolve("someSeries");
        Files.createDirectories(seriesDir);
        String movie1Name = "seriesName.site.s03e04.BlueRay";
        Files.createFile(seriesDir.resolve(movie1Name + ".mkv"));
        String movie2Name = "seriesName.site.s03e05.BlueRay";
        Files.createFile(seriesDir.resolve(movie2Name + ".mkv"));
        String movie1SubContent = "S03E04";
        Files.write(seriesDir.resolve("subtitleName.site.S03E04.BlueRay.srt"), movie1SubContent.getBytes());
        String movie2SubContent = "S03E05";
        Files.write(seriesDir.resolve("subtitleName.site.S03E05.BlueRay.srt"), movie2SubContent.getBytes());

        String movieRegex = "^seriesName.*s(\\d+)e(\\d+).*\\.mkv$";
        String subtitleRegex = "^subtitleName.*s$1e$2.*BlueRay\\.srt$";
        String url = String.format("/set-subtitle/bulk?path=someSeries&movieRegex=%s&subtitleRegex=%s", movieRegex,
                                   subtitleRegex);
        ResponseEntity<List<List<FileModel>>> response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY,
                                                                              new ParameterizedTypeReference<>() {});
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals(movie1SubContent, Files.readString(seriesDir.resolve(movie1Name + ".srt")));
        assertEquals(movie2SubContent, Files.readString(seriesDir.resolve(movie2Name + ".srt")));
    }

    @Test
    public void testDelete() throws IOException {
      Path file = Files.createFile(rootDir.resolve("delete-test-file"));
      Path dir = Files.createDirectories(rootDir.resolve("delete-test-dir"));
      restTemplate.delete("/files?path=" + file.getFileName().toString() + "&path=" + dir.getFileName().toString());
      assertFalse(Files.exists(file));
      assertFalse(Files.exists(dir));
    }

    private List<FileModel> listFiles(String path) {
        ResponseEntity<List<FileModel>> resp = restTemplate.exchange("/files?path=" + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<FileModel>>() {
               });
        assertTrue(resp.getStatusCode().is2xxSuccessful());
        return resp.getBody();
    }

    private HttpStatus getHttpStatus(String path) {
        ResponseEntity<String> responseEntity = restTemplate.getForEntity("/files?path=" + path, String.class);
        return responseEntity.getStatusCode();
    }

}