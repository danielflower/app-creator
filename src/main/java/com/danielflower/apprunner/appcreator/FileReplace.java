package com.danielflower.apprunner.appcreator;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileReplace implements AutoCloseable {

    private final File file;
    private String content;

    public FileReplace(File file) throws IOException {
        this.file = file;
        this.content = FileUtils.readFileToString(file, UTF_8);
    }

    public void replaceAll(String old, String newValue) {
        content = content.replace(old, newValue);
    }

    @Override
    public void close() throws IOException {
        FileUtils.writeStringToFile(file, content, UTF_8);
    }
}
