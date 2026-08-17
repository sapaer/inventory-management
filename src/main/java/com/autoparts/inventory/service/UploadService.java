package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.config.AppProperties;
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
    private final AppProperties.Aws aws;

    public UploadService(AppProperties props) {
        this.aws = props.aws();
    }

    public Map<String, String> presign(UUID userId, String filename, String contentType) {
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw AppException.badRequest("INVALID_FILE_TYPE", "Only JPEG and PNG images are supported");
        }
        if (aws.accessKey() == null || aws.accessKey().isBlank()
                || aws.secretKey() == null || aws.secretKey().isBlank()
                || "local".equals(aws.accessKey())) {
            throw AppException.badRequest("AWS_NOT_CONFIGURED", "S3 upload is not configured");
        }
        String ext = "image/png".equals(contentType) ? ".png" : ".jpg";
        String key = "parts/" + userId + "/" + UUID.randomUUID() + ext;
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(aws.s3Region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(aws.accessKey(), aws.secretKey())))
                .build()) {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(aws.s3Bucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .putObjectRequest(objectRequest)
                    .build());
            return Map.of(
                    "upload_url", presigned.url().toString(),
                    "public_url", aws.cloudfrontUrl() + "/" + key,
                    "key", key,
                    "filename", filename == null ? "" : filename
            );
        }
    }
}
