package com.ashenox.starter.user.controller;

import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.dto.UserResponse;
import com.ashenox.starter.user.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        UserResponse response = adminUserService.create(request, principal.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
