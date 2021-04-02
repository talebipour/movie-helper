package com.github.talebipour.moviehelper.controller;

import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_1_CONTENT;
import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_1_NAME;
import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_2_CONTENT;
import static com.github.talebipour.moviehelper.controller.SubtitleProviderController.SUBTITLE_2_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.talebipour.moviehelper.model.FileModel;
import com.github.talebipour.moviehelper.model.FileModel.FileType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("directory.path", rootDir.toAbsolutePath().toString());
    }

    @BeforeEach
    void setup() throws IOException {
        Files.walk(rootDir).filter(path -> !path.equals(rootDir)).forEach(path -> {
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
    public void testSetSubtitle() throws IOException {
        Files.createFile(rootDir.resolve("movie.mkv"));
        Files.createFile(rootDir.resolve("subtitle.srt"));
        String url = "/set-subtitle?subtitle=subtitle.srt&movie=movie.mkv";
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
    public void testExtensionAndFilename() {
        String filename = "movie.1080.mp4";
        assertEquals("mp4", FileController.extension(filename));
        assertEquals("movie.1080", FileController.filenameWithoutExtension(filename));
    }

    @Test
    public void testDownloadSubtitle() throws IOException {
        ResponseEntity<String> entity =
                restTemplate.getForEntity("/download-subtitle?path=&url=" + restTemplate.getRootUri() +
                                          "/sample-subtitle.zip", String.class);
        assertTrue(entity.getStatusCode().is2xxSuccessful());
        Set<String> files = Arrays.stream(entity.getBody().split("\n")).collect(Collectors.toSet());
        assertEquals(new HashSet<>(asList(SUBTITLE_1_NAME, SUBTITLE_2_NAME)), files);
        assertEquals(SUBTITLE_1_CONTENT, new String(Files.readAllBytes(rootDir.resolve(SUBTITLE_1_NAME))));
        assertEquals(SUBTITLE_2_CONTENT, new String(Files.readAllBytes(rootDir.resolve(SUBTITLE_2_NAME))));
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