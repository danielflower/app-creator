package com.danielflower.apprunner.appcreator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;

import java.nio.file.Path;

public class Gitter {

    private final Git git;

    public Gitter(Git git) {
        this.git = git;
    }

    public Gitter addAll() throws GitAPIException {
        git.add().addFilepattern(".").call();
        return this;
    }
    public Gitter commit(String message) throws GitAPIException {
        git.commit()
            .setMessage(message)
            .setCommitter("AppRunner App Creator", "noemail@example.org")
            .call();
        return this;
    }
    public Gitter push() throws GitAPIException {
        git.push().call();
        return this;
    }

    public static Gitter init(Path dir, String originUrl) throws Exception {
        Git git = Git.init().setDirectory(dir.toFile()).setBare(false).call();
        RemoteAddCommand remoteAddCommand = git.remoteAdd();
        remoteAddCommand.setName("origin");
        remoteAddCommand.setUri(new URIish(originUrl));
        remoteAddCommand.call();
        return new Gitter(git);
    }
}
