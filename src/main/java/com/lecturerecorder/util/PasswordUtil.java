package com.lecturerecorder.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deliberately simple SHA-256 based hashing -- this is a prototype/demo
 * project, not a production auth system. For real deployment, swap this
 * for BCrypt via Spring Security.
 */
public class PasswordUtil {

    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean matches(String raw, String hash) {
        return hash(raw).equals(hash);
    }
}
