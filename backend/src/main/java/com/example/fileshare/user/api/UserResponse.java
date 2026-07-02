package com.example.fileshare.user.api;

import com.example.fileshare.user.domain.User;
import java.time.Instant;

public record UserResponse(
        Long userId,
        String email,
        String name,
        String role,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.email(),
                user.name(),
                user.role().name(),
                user.createdAt(),
                user.updatedAt()
        );
    }
}
