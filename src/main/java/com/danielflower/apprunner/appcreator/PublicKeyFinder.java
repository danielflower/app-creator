package com.danielflower.apprunner.appcreator;

import com.jcraft.jsch.Identity;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.io.output.WriterOutputStream;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PublicKeyFinder {
    public static String getPublicKey() throws Exception {
        List<String> publicKeys = new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
            }

            List<String> getPublicKeys() throws Exception {
                JSch jSch = createDefaultJSch(FS.DETECTED);
                List<String> keys = new ArrayList<>();
                for (Object o : jSch.getIdentityRepository().getIdentities()) {
                    Identity i = (Identity) o;
                    KeyPair keyPair = KeyPair.load(jSch, i.getName(), null);
                    StringBuilder sb = new StringBuilder();
                    try (StringBuilderWriter sbw = new StringBuilderWriter(sb);
                         OutputStream os = new WriterOutputStream(sbw, "UTF-8")) {
                        keyPair.writePublicKey(os, keyPair.getPublicKeyComment());
                    } finally {
                        keyPair.dispose();
                    }
                    keys.add(sb.toString().trim());
                }
                return keys;
            }
        }.getPublicKeys();
        if (publicKeys.size() == 0) {
            throw new RuntimeException("Could not figure out the current public key");
        }
        return publicKeys.get(0);
    }
}
