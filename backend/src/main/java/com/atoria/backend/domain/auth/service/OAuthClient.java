package com.atoria.backend.domain.auth.service;

import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OAuthClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;

    public OAuthClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public OAuthUserInfo getUserInfo(
            OAuthProvider provider,
            OAuthClientProperties.OAuthClient client,
            String code
    ) {
        String accessToken = requestAccessToken(provider, client, code);
        Map<String, Object> attributes = requestUserInfo(provider, accessToken);
        return extractUserInfo(provider, attributes);
    }

    private String requestAccessToken(
            OAuthProvider provider,
            OAuthClientProperties.OAuthClient client,
            String code
    ) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", client.clientId());
        formData.add("redirect_uri", client.redirectUri());
        formData.add("code", code);

        if (client.hasClientSecret()) {
            formData.add("client_secret", client.clientSecret());
        }

        Map<String, Object> tokenResponse = webClient.post()
                .uri(provider.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .block();

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }

        return tokenResponse.get("access_token").toString();
    }

    private Map<String, Object> requestUserInfo(OAuthProvider provider, String accessToken) {
        Map<String, Object> userInfoResponse = webClient.get()
                .uri(provider.userInfoUri())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .block();

        if (userInfoResponse == null) {
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }

        return userInfoResponse;
    }

    private OAuthUserInfo extractUserInfo(OAuthProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> googleUserInfo(attributes);
            case KAKAO -> kakaoUserInfo(attributes);
        };
    }

    private OAuthUserInfo googleUserInfo(Map<String, Object> attributes) {
        return new OAuthUserInfo(
                stringValue(attributes.get("id")),
                stringValue(attributes.get("email")),
                stringValue(attributes.get("name"))
        );
    }

    private OAuthUserInfo kakaoUserInfo(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = mapValue(attributes.get("kakao_account"));
        Map<String, Object> profile = mapValue(kakaoAccount.get("profile"));

        return new OAuthUserInfo(
                stringValue(attributes.get("id")),
                stringValue(kakaoAccount.get("email")),
                stringValue(profile.get("nickname"))
        );
    }

    private String stringValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new BusinessException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
        return (Map<String, Object>) map;
    }
}
