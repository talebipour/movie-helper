package com.github.talebipour.moviehelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SubtitleProviderController {

    static final String SUBTITLE_DIRECTORY = "subs";

    static final String SUBTITLE_1_CONTENT = "sub1";
    static final String SUBTITLE_1_NAME = "sub1.srt";

    static final String SUBTITLE_2_CONTENT = "sub2";
    static final String SUBTITLE_2_NAME = "sub2.srt";

    @GetMapping(value = "/sample-subtitle.zip", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public Resource subtitle() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zaos = new ZipOutputStream(baos)) {
            writeEntry(zaos, SUBTITLE_1_CONTENT, SUBTITLE_DIRECTORY + "/" + SUBTITLE_1_NAME);
            writeEntry(zaos, "Some readme!", "README");
            writeEntry(zaos, SUBTITLE_2_CONTENT, SUBTITLE_DIRECTORY + "/" + SUBTITLE_2_NAME);
        }
        return new ByteArrayResource(baos.toByteArray());
    }

    private void writeEntry(ZipOutputStream zaos, String content, String fileName) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        byte[] bytes = content.getBytes();
        entry.setSize(bytes.length);
        zaos.putNextEntry(entry);
        zaos.write(bytes);
    }

}
