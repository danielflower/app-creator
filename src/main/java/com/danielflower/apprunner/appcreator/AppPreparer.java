package com.danielflower.apprunner.appcreator;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AppPreparer {
    public static final Logger log = LoggerFactory.getLogger(AppPreparer.class);
    public static final Random rng = new Random();
    private final String appName;
    private final String sampleType;
    private final InputStream zipSample;
    private final Path tempDir;

    public AppPreparer(String appName, String sampleType, InputStream zipSample, Path tempDir) {
        this.appName = appName;
        this.sampleType = sampleType;
        this.zipSample = zipSample;
        this.tempDir = tempDir;
    }

    public Path prepare() throws IOException {

        Files.createDirectories(tempDir);

        Path target = Files.createTempDirectory(tempDir, appName);
        log.info("Going to unzip a " + sampleType + " app named " + appName + " to " + target);

        unzipToTarget(target, zipSample);

        switch (sampleType) {
            case "maven": {
                customiseMavenProject(target, appName);
                break;
            }
            default: {
                log.warn("No customisation available for " + sampleType);
            }
        }

        return target;
    }

    private void customiseMavenProject(Path appDir, String appName) throws IOException {
        String packageName = appName.replace("-", "").replace("_", "").toLowerCase();
        if (packageName.length() == 0 || packageName.matches("^[0-9].*$")) {
            packageName = "app" + packageName;
        }
        FileUtils.moveDirectory(appDir.resolve("./src/main/java/samples").toFile(), appDir.resolve("./src/main/java/" + packageName).toFile());
        try (FileReplace fr = new FileReplace(appDir.resolve("./pom.xml").toFile())) {
            fr.replaceAll("my-maven-app", appName);
            fr.replaceAll("<groupId>samples</groupId>", "<groupId>apprunner.apps</groupId>");
            fr.replaceAll("samples.App", packageName + ".App");
        }
        try (FileReplace fr = new FileReplace(appDir.resolve("./src/main/resources/web/index.html").toFile())) {
            fr.replaceAll("App Runner App", appName);
            fr.replaceAll("My Maven App", appName);
        }
        try (FileReplace fr = new FileReplace(appDir.resolve("./src/main/java/" + packageName + "/App.java").toFile())) {
            fr.replaceAll("package samples;", "package " + packageName + ";");
            fr.replaceAll("8081", String.valueOf(rng.nextInt(9999) + 1000));
            fr.replaceAll("\"my-app\"", "\"" + appName + "\"");
        }
    }

    private static void unzipToTarget(Path target, InputStream zipSample) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipSample)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                Path newFile = target.resolve(fileName);
                boolean isDir = zipEntry.isDirectory();
                if (isDir) {
                    System.out.println("Creating dir " + newFile.toFile().getCanonicalPath());
                    Files.createDirectories(newFile);
                } else {
                    System.out.println("Writing " + newFile.toFile().getCanonicalPath());
                    try (FileOutputStream fos = new FileOutputStream(newFile.toFile())) {
                        byte[] buffer = new byte[8 * 1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }
}
