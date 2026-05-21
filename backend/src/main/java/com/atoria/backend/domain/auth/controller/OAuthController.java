package com.atoria.backend.domain.auth.controller;

import com.atoria.backend.domain.auth.dto.response.AuthTokenResponse;
import com.atoria.backend.domain.auth.dto.response.OAuthAuthorizationResponse;
import com.atoria.backend.domain.auth.service.AuthService;
import com.atoria.backend.global.response.ApiResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/oauth2")
public class OAuthController {

    private static final String APP_DEEP_LINK = "culture://oauth/callback";

    private final AuthService authService;

    public OAuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/authorization/{provider}")
    public ApiResponse<OAuthAuthorizationResponse> authorize(@PathVariable String provider) {
        return ApiResponse.success("OAuth 로그인 URL 생성 성공", authService.authorizeOAuth(provider));
    }

    @GetMapping("/callback/{provider}")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        try {
            AuthTokenResponse tokens = authService.handleOAuthCallback(provider, code, state);
            String deepLink = UriComponentsBuilder
                    .fromUriString(APP_DEEP_LINK)
                    .queryParam("accessToken", tokens.accessToken())
                    .queryParam("refreshToken", tokens.refreshToken())
                    .queryParam("userId", tokens.user().userId())
                    .queryParam("nickname", tokens.user().nickname())
                    .build()
                    .encode()
                    .toUriString();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(deepLink))
                    .build();
        } catch (Exception ex) {
            String errorLink = UriComponentsBuilder
                    .fromUriString(APP_DEEP_LINK)
                    .queryParam("error", ex.getMessage() != null ? ex.getMessage() : "oauth_failed")
                    .build()
                    .encode()
                    .toUriString();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(errorLink))
                    .build();
        }
    }
}
