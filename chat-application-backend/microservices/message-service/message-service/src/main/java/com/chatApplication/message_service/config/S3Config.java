package com.chatApplication.message_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Optional;

/**
 * AWS S3 configuration for Presigned URL generation.
 * <p>
 * Configures both {@link S3Presigner} (for generating presigned PUT URLs)
 * and {@link S3Client} (for direct S3 operations if needed).
 * <p>
 * Supports:
 * - Real AWS S3 with access key/secret key credentials
 * - Local MinIO/S3-compatible storage via custom endpoint override
 * <p>
 * Credentials are injected from application.properties and MUST NOT
 * be hardcoded. In production, use IAM roles or environment variables.
 */
@Slf4j
@Configuration
public class S3Config {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    @Value("${aws.s3.endpoint:}")
    private String endpointOverride;

    /**
     * Creates an S3Presigner bean for generating presigned URLs.
     * <p>
     * The presigner uses the same credentials and region as the S3Client
     * but is a separate component optimized for URL signing operations.
     *
     * @return configured S3Presigner instance
     */
    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)));

        // Override endpoint for local S3-compatible storage (MinIO, LocalStack)
        Optional<URI> endpoint = resolveEndpoint();
        endpoint.ifPresent(builder::endpointOverride);

        S3Presigner presigner = builder.build();

        log.info("S3Presigner initialized: region={}, bucket={}, endpoint={}",
                region, bucketName,
                endpoint.map(URI::toString).orElse("default-aws"));

        return presigner;
    }

    /**
     * Creates an S3Client bean for direct S3 operations.
     * <p>
     * Used for health checks, bucket validation, and any
     * direct S3 interactions beyond presigned URL generation.
     *
     * @return configured S3Client instance
     */
    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)));

        // Override endpoint for local S3-compatible storage (MinIO, LocalStack)
        Optional<URI> endpoint = resolveEndpoint();
        endpoint.ifPresent(uri -> builder
                .endpointOverride(uri)
                .forcePathStyle(true)); // Required for MinIO

        S3Client client = builder.build();

        log.info("S3Client initialized: region={}, endpoint={}",
                region,
                endpoint.map(URI::toString).orElse("default-aws"));

        return client;
    }

    /**
     * Resolves the S3 endpoint URI from configuration.
     * Returns empty Optional if no override is configured (uses real AWS).
     *
     * @return Optional containing the endpoint URI, or empty for default AWS
     */
    private Optional<URI> resolveEndpoint() {
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            URI uri = URI.create(endpointOverride);
            log.info("Using custom S3 endpoint: {}", uri);
            return Optional.of(uri);
        }
        return Optional.empty();
    }
}
