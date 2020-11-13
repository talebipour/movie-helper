package com.github.talebipour.moviehelper;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

import com.github.talebipour.moviehelper.FileModel.FileType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
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

    private List<FileModel> listFiles(String path) {
        ResponseEntity<List<FileModel>> resp = restTemplate.exchange("/files/" + path, HttpMethod.GET, null,
                                                                     new ParameterizedTypeReference<List<FileModel>>() {
                                                                     });
        assertTrue(resp.getStatusCode().is2xxSuccessful());
        return resp.getBody();
    }

    private HttpStatus getHttpStatus(String path) {
        ResponseEntity<String> responseEntity = restTemplate.getForEntity("/files/" + path, String.class);
        return responseEntity.getStatusCode();
    }
}