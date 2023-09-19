package com.github.talebipour.moviehelper.util.opensubtitles;

import com.github.talebipour.moviehelper.model.Subtitle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenSubtitlesClientTest {

    @Autowired
    private OpenSubtitlesClient client;

    @Test
    void searchByFileHash() {
        List<Subtitle> subtitles = client.searchByFileHash("3b8203c135f0969b", "en");
        assertFalse(subtitles.isEmpty());
    }
}