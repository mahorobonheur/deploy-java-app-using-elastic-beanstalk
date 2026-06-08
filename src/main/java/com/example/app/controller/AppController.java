package com.example.app.controller;

import com.example.app.service.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AppController {

    private static final String APP_VERSION = "1.0.0";

    private final S3Service s3Service;

    @Value("${spring.application.name:eb-demo-app}")
    private String appName;

    public AppController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * Root endpoint – confirms the app is running and shows the deployed version.
     * This is what the live-review panel will hit first.
     */
    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", appName);
        response.put("version", APP_VERSION);
        response.put("status", "UP");
        response.put("deployedAt", Instant.now().toString());
        response.put("message", "Deployment successful! App is running on AWS Elastic Beanstalk.");
        return response;
    }

    /**
     * Health-check endpoint – Elastic Beanstalk polls this to determine
     * instance health. Returns HTTP 200 with a simple body.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "version", APP_VERSION);
    }

    /**
     * S3 integration endpoint – lists objects in the configured bucket.
     * Demonstrates the external service connection for the optional challenge.
     */
    @GetMapping("/s3")
    public Map<String, Object> s3Info() {
        List<String> objects = s3Service.listObjects();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("bucket", s3Service.getBucketName());
        response.put("objectCount", objects.size());
        response.put("objects", objects);
        return response;
    }
}
