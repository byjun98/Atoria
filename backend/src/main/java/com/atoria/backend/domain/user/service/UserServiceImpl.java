package com.atoria.backend.domain.user.service;

import com.atoria.backend.domain.auth.service.RefreshTokenStore;
import com.atoria.backend.domain.user.dto.request.UserDeleteRequest;
import com.atoria.backend.domain.user.dto.request.UserPasswordChangeRequest;
import com.atoria.backend.domain.user.dto.request.UserProfileUpdateRequest;
import com.atoria.backend.domain.user.dto.response.UserProfileResponse;
import com.atoria.backend.domain.user.entity.User;
import com.atoria.backend.domain.user.repository.UserRepository;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Override
    public UserProfileResponse getMyProfile(Long userId) {
        User user = getActiveUser(userId);
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname()
        );
    }

    @Override
    @Transactional
    public void updateMyProfile(Long userId, UserProfileUpdateRequest request) {
        User user = getActiveUser(userId);
        validateNickname(user, request.nickname());
        user.updateProfile(request.nickname());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, UserPasswordChangeRequest request) {
        User user = getActiveUser(userId);
        validateCurrentPassword(request.currentPassword(), user.getPassword());
        validatePasswordConfirm(request.newPassword(), request.newPasswordConfirm());

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenStore.delete(user.getId());
    }

    @Override
    @Transactional
    public void deleteAccount(Long userId, UserDeleteRequest request) {
        User user = getActiveUser(userId);
        validateCurrentPassword(request.password(), user.getPassword());

        user.delete();
        refreshTokenStore.delete(user.getId());
    }

    private User getActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateNickname(User user, String nickname) {
        if (!user.getNickname().equals(nickname) && userRepository.existsByNicknameAndDeletedFalse(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
        }
    }

    private void validateCurrentPassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
    }

    private void validatePasswordConfirm(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
    }
}
