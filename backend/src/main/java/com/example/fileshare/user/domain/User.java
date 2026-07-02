package com.example.fileshare.user.domain;

import java.time.Instant;

public class User {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private String name;
    private final UserRole role;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(Long id, String email, String passwordHash, String name, UserRole role, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String name() {
        return name;
    }

    public UserRole role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void updateName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }
}
