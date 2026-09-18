package dev.worldbuilder.bridge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Authentication {
    public static boolean tokenMatches(String expected, String supplied) {
        if (expected == null || supplied == null || expected.length() < 16) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
    public static String loadOrCreateLocalToken() throws java.io.IOException {
        Path path = Path.of(System.getProperty("user.home"), ".minecraft-ai-world-builder", "local-token");
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            String token = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (token.length() >= 16) return token;
            throw new java.io.IOException("Local Bridge token file is invalid: " + path);
        }
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try { Files.writeString(path, token + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (java.nio.file.FileAlreadyExistsException race) { return Files.readString(path, StandardCharsets.UTF_8).trim(); }
        return token;
    }
    private Authentication() {}
}
