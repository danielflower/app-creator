package com.danielflower.apprunner.appcreator;

import io.muserver.*;
import io.muserver.Cookie;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static io.muserver.MuServerBuilder.httpServer;
import static io.muserver.Mutils.urlEncode;
import static io.muserver.handlers.ResourceHandlerBuilder.fileOrClasspath;

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
        URI githubApiUrl = URI.create("https://api.github.com");


        log.info("Starting " + appName);
        MuServer server = httpServer()
            .withHttpPort(Integer.parseInt(settings.getOrDefault("APP_PORT", "8081")))
            .addHandler(Method.GET, "/app-creator/redirect-to-github", (request, response, pp) -> {
                response.redirect(new HttpUrl.Builder()
                    .scheme(githubUrl.split("://")[0])
                    .host(githubUrl.split("://")[1])
                    .addEncodedPathSegments("login/oauth/authorize")
                    .addQueryParameter("client_id", githubOAuthClientID)
                    .addQueryParameter("scope", "write:repo_hook public_repo")
                    .addQueryParameter("redirect_uri", request.uri().resolve("/app-creator/from-github").toString())
                    .addQueryParameter("state", crsfToken)
                    .build().uri());
            })
            .addHandler(Method.GET, "/app-creator/from-github", (request, response, pp) -> {
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
            })
            .addHandler(Method.POST, "/app-creator/create", (request, response, pp) -> {
                String githubToken = request.cookie("GithubToken").get();
                String name = request.form().get("appName");
                String sampleUrl = request.form().get("sampleUrl");
                String sampleType = sampleUrl.substring(sampleUrl.lastIndexOf('/') + 1).replace(".zip", "");
                Path dir;
                try (Response sampleResp = httpClient.newCall(new Request.Builder()
                    .url(sampleUrl)
                    .build()).execute()) {
                    AppPreparer appPreparer;
                    try (InputStream inputStream = sampleResp.body().byteStream()) {
                        appPreparer = new AppPreparer(name, sampleType, inputStream, tempDir);
                        dir = appPreparer.prepare();
                    }
                }
                GithubApi githubApi = new GithubApi(httpClient, githubApiUrl);
                githubApi.createRepo(githubToken, name);
                githubApi.addFiles(githubToken, dir);

                String deployUrl;
                try (Response appRunnerResp = httpClient.newCall(new Request.Builder()
                    .url(appRunnerUri.resolve("/api/v1/apps").toString())
                    .post(new FormBody.Builder()
                        .add("appName", name)
                        .add("gitUrl", githubApi.httpsUrl().toString())
                        .build())
                    .build()).execute()) {
                    int code = appRunnerResp.code();
                    String body = appRunnerResp.body().string();
                    if (code != 201) {
                        throw new RuntimeException("Error registering apprunner app: " + code + " " + body);
                    }
                    JSONObject app = new JSONObject(body);
                    log.info("Created apprunner app: " + app);
                    deployUrl = app.getString("deployUrl");
                }

                githubApi.addWebHook(githubToken, deployUrl);

                response.redirect(appRunnerUri.resolve("/home/" + urlEncode(name) + ".html"));
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