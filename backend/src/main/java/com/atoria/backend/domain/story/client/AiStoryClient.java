package com.atoria.backend.domain.story.client;

import com.atoria.backend.global.config.AiProperties;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
public class AiStoryClient {

    private final WebClient webClient;
    private final AiProperties aiProperties;

    public AiStoryClient(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        this.webClient = webClientBuilder
                .baseUrl(aiProperties.baseUrl())
                .build();
        this.aiProperties = aiProperties;
    }

    public AiStoryIntroResponse createStoryIntro(AiStoryIntroRequest request) {
        try {
            AiStoryIntroResponse response = webClient.post()
                    .uri("/story/intro")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiStoryIntroResponse.class)
                    .block(Duration.ofSeconds(aiProperties.timeoutSeconds()));

            if (response == null) {
                throw new BusinessException(ErrorCode.AI_STORY_GENERATION_FAILED);
            }

            return response;
        } catch (WebClientException | IllegalStateException exception) {
            throw new BusinessException(ErrorCode.AI_STORY_GENERATION_FAILED);
        }
    }
}
