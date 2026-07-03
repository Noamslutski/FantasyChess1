package com.fantasychess.israel.data.model;

/**
 * Local account. There is no server yet, so credentials are stored on-device
 * only (password as a SHA-256 hash) — the login screen gates the app and the
 * username identifies the user on the market.
 */
public class UserProfile {

    public final String username;
    public final String passwordHash;
    public final long createdAtEpochMs;

    public UserProfile(String username, String passwordHash, long createdAtEpochMs) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAtEpochMs = createdAtEpochMs;
    }
}
