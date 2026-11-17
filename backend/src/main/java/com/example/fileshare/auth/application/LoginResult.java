package com.example.fileshare.auth.application;

import com.example.fileshare.user.domain.User;

public record LoginResult(
        User user,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
