package com.danielflower.apprunner.appcreator;

import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ronin.muserver.HeaderNames;

import java.io.IOException;
import java.net.URI;

public class GithubApi {
    public static final Logger log = LoggerFactory.getLogger(GithubApi.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final URI apiUrl;
    private JSONObject repoInfo;
    private String addedKeyUrl;

    public GithubApi(OkHttpClient client, URI apiUrl) {
        this.client = client;
        this.apiUrl = apiUrl;
    }

    public String sshUrl() {
        return repoInfo.getString("ssh_url");
    }
    public URI keysUri() {
        return URI.create(repoInfo.getString("keys_url").replace("{/key_id}", ""));
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

    public void addDeployKey(String oauthToken, String publicKey) throws IOException {
        try (Response resp = client.newCall(
            new Request.Builder()
                .url(keysUri().toString())
                .addHeader(HeaderNames.AUTHORIZATION.toString(), "token " + oauthToken)
                .post(RequestBody.create(JSON,
                    new JSONObject()
                        .put("title", "appcreator@apprunner")
                        .put("key", publicKey)
                        .put("read_only", false)
                        .toString(2)
                ))
                .build()
        ).execute()) {
            JSONObject body = new JSONObject(resp.body().string());
            if (resp.code() != 201) {
                log.error("Could not add public key - response code was " + resp.code() + " and body was: " + body.toString(4));
                throw new RuntimeException("Could not add key to repo " + resp.code());
            } else {
                log.info("Created git repo: " + body);
                this.addedKeyUrl = body.getString("url");
            }
        }
    }

    public void deleteAddedKey(String oauthToken) throws IOException {
        try (Response resp = client.newCall(
            new Request.Builder()
                .url(addedKeyUrl)
                .addHeader(HeaderNames.AUTHORIZATION.toString(), "token " + oauthToken)
                .delete()
                .build()
        ).execute()) {
            if (resp.code() != 204) {
                log.warn("Could not delete the added public key - response code was " + resp.code());
            }
        }

    }
}
