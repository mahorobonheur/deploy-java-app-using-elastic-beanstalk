package com.example.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that integrates with Amazon S3.
 * The bucket name and AWS region are provided through Elastic Beanstalk
 * environment variables (S3_BUCKET_NAME, AWS_REGION).
 */
@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;

    public S3Service(
            @Value("${aws.region:us-east-1}") String region,
            @Value("${s3.bucket.name:}") String bucketName) {

        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Returns the first 20 object keys in the configured S3 bucket.
     * Returns an empty list when no bucket name is configured, so the app
     * still starts cleanly during local development.
     */
    public List<String> listObjects() {
        if (bucketName == null || bucketName.isBlank()) {
            return Collections.singletonList("(S3_BUCKET_NAME not configured)");
        }

        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(20)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            return response.contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return List.of("Error listing objects: " + e.getMessage());
        }
    }

    public String getBucketName() {
        return bucketName == null || bucketName.isBlank() ? "(not set)" : bucketName;
    }
}
