package com.github.talebipour.moviehelper.util.opensubtitles;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.talebipour.moviehelper.model.Subtitle;

import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResponse {
    private int totalCount;
    private List<Item> data;

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<Item> getData() {
        return data;
    }

    public void setData(List<Item> data) {
        this.data = data;
    }


    public static class Item {
        private String id;
        private Attributes attributes;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Attributes getAttributes() {
            return attributes;
        }

        public void setAttributes(Attributes attributes) {
            this.attributes = attributes;
        }

        public static class Attributes {
            private String release;
            private List<File> files;

            public String getRelease() {
                return release;
            }

            public void setRelease(String release) {
                this.release = release;
            }

            public List<File> getFiles() {
                return files;
            }

            public void setFiles(List<File> files) {
                this.files = files;
            }

            public static class File {
                private String fileId;
                private String fileName;

                public String getFileId() {
                    return fileId;
                }

                public void setFileId(String fileId) {
                    this.fileId = fileId;
                }

                public String getFileName() {
                    return fileName;
                }

                public void setFileName(String fileName) {
                    this.fileName = fileName;
                }
            }
        }
    }

    public List<Subtitle> toSubtitles() {
        return  data.stream().flatMap(item -> item.getAttributes().getFiles().stream().map(file -> {
            var sub = new Subtitle();
            sub.setName(item.getAttributes().getRelease());
            sub.setFileId(file.getFileId());
            return sub;
        })).collect(Collectors.toList());
    }
}
