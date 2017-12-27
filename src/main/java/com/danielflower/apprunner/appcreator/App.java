package com.danielflower.apprunner.appcreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ronin.muserver.MuServer;

import java.util.Map;

import static ronin.muserver.MuServerBuilder.httpServer;
import static ronin.muserver.handlers.ResourceHandler.fileOrClasspath;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    public static void main(String[] args) throws Exception {
        Map<String, String> settings = System.getenv();
        String appName = settings.getOrDefault("APP_NAME", "app-creator");

        log.info("Starting " + appName);
        MuServer server = httpServer()
            .withHttpConnection(Integer.parseInt(settings.getOrDefault("APP_PORT", "8081")))
            .addHandler(fileOrClasspath("src/main/resources/web", "/web").build())
            .start();
        log.info("Started app at " + server.httpUri().resolve("/" + appName));

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }


}