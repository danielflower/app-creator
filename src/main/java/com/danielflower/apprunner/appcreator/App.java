package com.danielflower.apprunner.appcreator;

import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ronin.muserver.*;
import ronin.muserver.Cookie;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static ronin.muserver.MuServerBuilder.httpServer;
import static ronin.muserver.handlers.ResourceHandler.fileOrClasspath;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final String crsfToken = UUID.randomUUID().toString();

    public static void main(String[] args) throws Exception {
        Map<String, String> settings = System.getenv();
        String appName = settings.getOrDefault("APP_NAME", "app-creator");
        Path tempDir = Paths.get(settings.getOrDefault("TEMP", "target/temp"));

        String githubOAuthClientID = settings.get("APP_CREATOR_GITHUB_OAUTH_CLIENT_ID");
        String githubOAuthClientSecret = settings.get("APP_CREATOR_GITHUB_OAUTH_CLIENT_SECRET");
        if (githubOAuthClientID == null || githubOAuthClientSecret == null) {
            throw new RuntimeException("The following environment variables (which can be created in GitHub as OAuth apps) must be set: APP_CREATOR_GITHUB_OAUTH_CLIENT_ID and APP_CREATOR_GITHUB_OAUTH_CLIENT_SECRET");
        }
        URI appRunnerUri = URI.create("https://apprunner.co.nz");
        String githubUrl = "https://github.com";

        log.info("Starting " + appName);
        MuServer server = httpServer()
            .withHttpConnection(Integer.parseInt(settings.getOrDefault("APP_PORT", "8081")))
            .addHandler(Method.GET, "/app-creator/redirect-to-github", (request, response) -> {
                response.redirect(new HttpUrl.Builder()
                    .scheme(githubUrl.split("://")[0])
                    .host(githubUrl.split("://")[1])
                    .addEncodedPathSegments("login/oauth/authorize")
                    .addQueryParameter("client_id", githubOAuthClientID)
                    .addQueryParameter("scope", "write:repo_hook repo")
                    .addQueryParameter("redirect_uri", request.uri().resolve("/app-creator/from-github").toString())
                    .addQueryParameter("state", crsfToken)
                    .build().uri());
                return true;
            })
            .addHandler(Method.GET, "/app-creator/from-github", (request, response) -> {
                String code = request.parameter("code");
                String state = request.parameter("state");
                if (code.isEmpty() || state.isEmpty() || !state.equals(crsfToken)) {
                    response.redirect("/app-creator/error.html");
                } else {
                    Response tokenResponse = httpClient.newCall(
                        new Request.Builder()
                            .post(new FormBody.Builder()
                                .add("client_id", githubOAuthClientID)
                                .add("client_secret", githubOAuthClientSecret)
                                .add("code", code)
                                .add("state", crsfToken)
                                .build())
                            .url(githubUrl + "/login/oauth/access_token")
                            .addHeader(HeaderNames.ACCEPT.toString(), ContentTypes.APPLICATION_JSON.toString())
                        .build()
                    ).execute();
                    response.contentType(ContentTypes.TEXT_PLAIN);
                    String token = new JSONObject(tokenResponse.body().string()).getString("access_token");
                    response.addCookie(new Cookie("GithubToken", token));
                    response.redirect("/app-creator/create.html");
                }
                return true;
            })
            .addHandler(Method.POST, "/app-creator/create", (request, response) -> {
                String githubToken = request.cookie("GithubToken").get().value();
                String name = request.formValue("appName");
                String sampleUrl = request.formValue("sampleUrl");
                String sampleType = sampleUrl.substring(sampleUrl.lastIndexOf('/') + 1).replace(".zip", "");
                try (Response sampleResp = httpClient.newCall(new Request.Builder()
                    .url(sampleUrl)
                    .build()).execute()) {
                    AppPreparer appPreparer;
                    try (InputStream inputStream = sampleResp.body().byteStream()) {
                        appPreparer = new AppPreparer(name, sampleType, inputStream, tempDir);
                    }
                    Path dir = appPreparer.prepare();
                }

                return true;
            })
            .addHandler(
                fileOrClasspath("src/main/resources/web", "/web")
                    .withPathToServeFrom("/" + appName)
                    .build())
            .start();
        log.info("Started app at " + server.httpUri().resolve("/" + appName + "/"));

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }


}