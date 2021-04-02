package com.github.talebipour.moviehelper.model;

import java.util.Objects;

public class FileModel {

    private String name;
    private String path;
    private FileType type;
    private long size;

    public FileModel() {
    }

    public FileModel(String name, String path, FileType type, long size) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public enum FileType {
        REGULAR, DIRECTORY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FileModel fileModel = (FileModel) o;
        return path.equals(fileModel.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }


    @Override
    public String toString() {
        return "FileModel{" +
               "name='" + name + '\'' +
               ", path='" + path + '\'' +
               ", type=" + type +
               ", size=" + size +
               '}';
    }
}
