package com.ashenox.starter.user.service;

import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.dto.UserResponse;
import com.ashenox.starter.user.model.Role;

public interface AdminUserService {

    UserResponse create(CreateUserRequest request, Role actorRole);
}
