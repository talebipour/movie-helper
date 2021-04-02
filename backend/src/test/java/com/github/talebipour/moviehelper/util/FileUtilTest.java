package com.github.talebipour.moviehelper.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FileUtilTest {

    @Test
    public void testExtensionAndFilename() {
        String filename = "movie.1080.mp4";
        assertEquals("mp4", FileUtil.extension(filename));
        assertEquals("movie.1080", FileUtil.filenameWithoutExtension(filename));
    }

}