package com.github.talebipour.moviehelper.model;

import java.util.Objects;

public class DownloadStatus {

    private String url;

    private FileModel file;

    private Status status;

    private boolean rangeSupported;

    private int progressPercent;

    private String message;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public FileModel getFile() {
        return file;
    }

    public void setFile(FileModel file) {
        this.file = file;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isRangeSupported() {
        return rangeSupported;
    }

    public void setRangeSupported(boolean rangeSupported) {
        this.rangeSupported = rangeSupported;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DownloadStatus that = (DownloadStatus) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    public enum Status {
        IN_PROGRESS, COMPLETED, FAILED
    }
}
