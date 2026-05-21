package com.atoria.backend.domain.user.controller;

import com.atoria.backend.domain.user.dto.request.UserDeleteRequest;
import com.atoria.backend.domain.user.dto.request.UserPasswordChangeRequest;
import com.atoria.backend.domain.user.dto.request.UserProfileUpdateRequest;
import com.atoria.backend.domain.user.dto.response.UserProfileResponse;
import com.atoria.backend.domain.user.service.UserService;
import com.atoria.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success("사용자 정보 조회 성공", userService.getMyProfile(userId));
    }

    @PutMapping("/me")
    public ApiResponse<Void> updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        userService.updateMyProfile(userId, request);
        return ApiResponse.success("사용자 정보 수정 완료", null);
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserPasswordChangeRequest request
    ) {
        userService.changePassword(userId, request);
        return ApiResponse.success("비밀번호 변경 완료", null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDeleteRequest request
    ) {
        userService.deleteAccount(userId, request);
        return ApiResponse.success("회원 탈퇴 완료", null);
    }
}
