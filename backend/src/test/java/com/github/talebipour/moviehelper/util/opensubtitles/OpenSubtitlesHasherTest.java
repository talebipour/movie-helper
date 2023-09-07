package com.github.talebipour.moviehelper.util.opensubtitles;

import org.junit.jupiter.api.Test;

import java.io.File;

import static com.github.talebipour.moviehelper.util.opensubtitles.OpenSubtitlesHasher.computeHash;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenSubtitlesHasherTest {

    @Test
    void computeHashFile() throws Exception {
        String hash = computeHash(new File("src/test/resources/sample-mp4-file-small.mp4"));
        assertEquals(16, hash.length());
        System.out.println(hash);
    }
}