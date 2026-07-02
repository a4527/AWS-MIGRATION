package com.example.fileshare.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @NotBlank
        @Size(max = 100)
        String name
) {
}
