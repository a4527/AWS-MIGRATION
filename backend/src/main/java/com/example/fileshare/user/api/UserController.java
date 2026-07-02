package com.example.fileshare.user.api;

import com.example.fileshare.common.api.ApiResponse;
import com.example.fileshare.config.security.AuthUserPrincipal;
import com.example.fileshare.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ApiResponse.ok(UserResponse.from(userService.getById(principal.userId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.ok(UserResponse.from(userService.getById(id)));
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid @RequestBody UpdateMeRequest request
    ) {
        return ApiResponse.ok(UserResponse.from(userService.updateName(principal.userId(), request.name())), "Profile updated");
    }
}
