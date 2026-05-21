package com.atoria.backend.domain.user.service;

import com.atoria.backend.domain.user.dto.request.UserDeleteRequest;
import com.atoria.backend.domain.user.dto.request.UserPasswordChangeRequest;
import com.atoria.backend.domain.user.dto.request.UserProfileUpdateRequest;
import com.atoria.backend.domain.user.dto.response.UserProfileResponse;

public interface UserService {

    UserProfileResponse getMyProfile(Long userId);

    void updateMyProfile(Long userId, UserProfileUpdateRequest request);

    void changePassword(Long userId, UserPasswordChangeRequest request);

    void deleteAccount(Long userId, UserDeleteRequest request);
}
