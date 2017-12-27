package com.danielflower.apprunner.appcreator;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AppPreparerTest {

    @Test
    public void unzipsAndRenamesFiles() throws IOException {

        try (InputStream zip = AppPreparerTest.class.getResourceAsStream("/samples/maven.zip")) {
            AppPreparer preparer = new AppPreparer("blah-app", "maven", zip, Paths.get("target/temp"));
            Path dir = preparer.prepare();
            assertThat(Files.isRegularFile(dir.resolve("./pom.xml")), is(true));
        }

    }
}