package com.atoria.backend.global.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        @NotBlank String bucket,
        @NotBlank String region,
        String uploadPrefix,
        @Min(1) long presignedUrlExpirationMinutes
) {
}
