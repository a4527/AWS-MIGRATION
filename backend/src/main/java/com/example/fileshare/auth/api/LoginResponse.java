package com.example.fileshare.auth.api;

import com.example.fileshare.user.api.UserResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
