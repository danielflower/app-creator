package com.danielflower.apprunner.appcreator;

import io.muserver.HeaderNames;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.muserver.Mutils.urlEncode;


public class GithubApi {
    public static final Logger log = LoggerFactory.getLogger(GithubApi.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final URI apiUrl;
    private JSONObject repoInfo;

    public GithubApi(OkHttpClient client, URI apiUrl) {
        this.client = client;
        this.apiUrl = apiUrl;
    }

    public String sshUrl() {
        return repoInfo.getString("ssh_url");
    }

    public URI httpsUrl() {
        return URI.create(repoInfo.getString("clone_url"));
    }

    public URI hooksUrl() {
        return URI.create(repoInfo.getString("hooks_url"));
    }

    public URI contentsURI() {
        return URI.create(repoInfo.getString("contents_url").replace("{+path}", ""));
    }

    public void createRepo(String oauthToken, String repoName) throws IOException {
        try (Response resp = client.newCall(
            new Request.Builder()
                .url(apiUrl.resolve("/user/repos").toString())
                .addHeader(HeaderNames.AUTHORIZATION.toString(), "token " + oauthToken)
                .post(RequestBody.create(JSON,
                    new JSONObject()
                        .put("name", repoName)
                        .put("description", "An AppRunner app")
                        .put("has_issues", false)
                        .put("has_projects", false)
                        .put("has_wiki", false)
                        .put("auto_init", false)
                        .put("private", false)
                        .toString(2)
                ))
                .build()
        ).execute()) {
            JSONObject body = new JSONObject(resp.body().string());
            if (resp.code() != 201) {
                log.error("Could not create repo called " + repoName + " - response code was " + resp.code() + " and body was: " + body.toString(4));
                throw new RuntimeException("Could not create repo called " + repoName + " - status from GitHub was " + resp.code());
            } else {
                log.info("Created git repo: " + body);
                this.repoInfo = body;
            }
        }
    }

    public void addFiles(String oauthToken, Path dir) throws IOException {
        Collection<File> files = FileUtils.listFiles(dir.toFile(), null, true);
        for (File file : files) {
            Path relative = dir.relativize(file.toPath());
            String repoPath = relative.toString().replace('\\', '/');
            String encodedRepoPath = Stream.of(repoPath.split("/")).map(p -> urlEncode(p)).collect(Collectors.joining("/"));
            System.out.println("relative = " + relative);

            String base64Content = Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(file));
            try (Response resp = client.newCall(
                new Request.Builder()
                    .url(contentsURI().resolve("./" + encodedRepoPath).toString())
                    .addHeader(HeaderNames.AUTHORIZATION.toString(), "token " + oauthToken)
                    .put(RequestBody.create(JSON,
                        new JSONObject()
                            .put("path", repoPath)
                            .put("message", "Initial app setup")
                            .put("content", base64Content)
                            .put("branch", "master")
                            .put("committer", new JSONObject()
                                .put("name", "AppRunner App Creator")
                                .put("email", "appcreator@example.org"))
                            .toString(2)
                    ))
                    .build()
            ).execute()) {

                JSONObject body = new JSONObject(resp.body().string());
                if (resp.code() != 201) {
                    log.error("Could not upload " + file.getCanonicalPath() + " - response code was " + resp.code() + " and body was: " + body.toString(4));
                    throw new RuntimeException("Could not upload " + file.getCanonicalPath() + " - status from GitHub was " + resp.code());
                } else {
                    log.info("Uploaded file: " + body);
                }
            }

        }
    }

    public void addWebHook(String oauthToken, String deployUrl) throws IOException {
        try (Response resp = client.newCall(
            new Request.Builder()
                .url(hooksUrl().toString())
                .addHeader(HeaderNames.AUTHORIZATION.toString(), "token " + oauthToken)
                .post(RequestBody.create(JSON,
                    new JSONObject()
                        .put("name", "web")
                        .put("config", new JSONObject()
                            .put("url", deployUrl)
                        )
                        .toString(2)
                ))
                .build()
        ).execute()) {
            JSONObject body = new JSONObject(resp.body().string());
            if (resp.code() != 201) {
                log.warn("Could not add web hook. Got " + resp.code() + " with " + body);
            } else {
                log.info("Created git web hook: " + body);
            }
        }

    }
}
