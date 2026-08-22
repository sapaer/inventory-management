package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.config.AwsProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class UploadService {
    private final AwsProperties aws;

    public UploadService(AppProperties props) {
        this.aws = props.getAws();
    }

    public Map<String, String> presign(UUID userId, String filename, String contentType) {
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw AppException.badRequest("INVALID_FILE_TYPE", "Only JPEG and PNG images are supported");
        }
        if (aws.getAccessKey() == null || aws.getAccessKey().isBlank()
                || aws.getSecretKey() == null || aws.getSecretKey().isBlank()
                || "local".equals(aws.getAccessKey())) {
            throw AppException.badRequest("AWS_NOT_CONFIGURED", "S3 upload is not configured");
        }
        String ext = "image/png".equals(contentType) ? ".png" : ".jpg";
        String key = "parts/" + userId + "/" + UUID.randomUUID() + ext;
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(aws.getS3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(aws.getAccessKey(), aws.getSecretKey())))
                .build()) {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(aws.getS3Bucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .putObjectRequest(objectRequest)
                    .build());
            return Map.of(
                    "upload_url", presigned.url().toString(),
                    "public_url", aws.getCloudfrontUrl() + "/" + key,
                    "key", key,
                    "filename", filename == null ? "" : filename
            );
        }
    }
}
