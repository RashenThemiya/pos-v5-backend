package com.pos.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;

    @Value("${r2.bucket}")
    private String bucket;

    @Value("${r2.public-url}")
    private String publicUrl;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    public String uploadItemImage(MultipartFile file) {
        return uploadImage(file, "item_images");
    }

    public String uploadVariantImage(MultipartFile file) {
        return uploadImage(file, "variant_images");
    }

    public String uploadCatalogImage(byte[] content, String fileName, String contentType) {
        if (content == null || content.length == 0) {
            return null;
        }
        String normalizedType = contentType == null ? "" : contentType.toLowerCase();
        if (!ALLOWED_IMAGE_TYPES.contains(normalizedType)) {
            throw new RuntimeException("Only JPG, JPEG, PNG, and WEBP catalog images are allowed");
        }

        String key = "item_images/catalog/" + UUID.randomUUID() + "_" + sanitizeFileName(fileName);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(normalizedType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
        return publicUrl + "/" + key;
    }

    private String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateImage(file);

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
        String sanitizedName = sanitizeFileName(originalName);
        String key = folder + "/" + UUID.randomUUID() + "_" + sanitizedName;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromBytes(file.getBytes())
            );

            return publicUrl + "/" + key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to R2", e);
        }
    }

    private void validateImage(MultipartFile file) {
        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Only JPG, JPEG, PNG, and WEBP images are allowed");
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
