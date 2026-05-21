package com.atoria.backend.domain.file.client;

import com.atoria.backend.global.config.AiProperties;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
public class AiEbookClient {

    private final WebClient webClient;
    private final AiProperties aiProperties;

    public AiEbookClient(WebClient.Builder webClientBuilder, AiProperties aiProperties) {
        this.webClient = webClientBuilder
                .baseUrl(aiProperties.baseUrl())
                .build();
        this.aiProperties = aiProperties;
    }

    public AiEbookJobResponse createEbook(AiEbookJobRequest request) {
        try {
            AiEbookJobResponse response = webClient.post()
                    .uri("/artifacts/ebook/jobs")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiEbookJobResponse.class)
                    .block(Duration.ofSeconds(aiProperties.timeoutSeconds()));

            if (response == null || !response.success()
                    || response.data() == null
                    || response.data().ebookContent() == null) {
                throw new BusinessException(ErrorCode.AI_EBOOK_GENERATION_FAILED);
            }

            return response;
        } catch (WebClientException | IllegalStateException exception) {
            throw new BusinessException(ErrorCode.AI_EBOOK_GENERATION_FAILED);
        }
    }
}
