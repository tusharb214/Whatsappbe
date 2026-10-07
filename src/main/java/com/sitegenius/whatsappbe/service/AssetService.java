package com.sitegenius.whatsappbe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class AssetService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    /**
     * Upload an image and return its public URL.
     *
     * Example:
     * http://localhost:8080/uploads/images/uuid.png
     */
    public String uploadImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Image file is required");
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !contentType.startsWith("image/")) {

            throw new RuntimeException(
                    "Only image files are allowed"
            );
        }

        try {

            // Common image storage
            Path directory =
                    Paths.get(uploadDir, "images");

            Files.createDirectories(directory);

            String originalName =
                    file.getOriginalFilename() == null
                            ? "image"
                            : file.getOriginalFilename();

            String extension = "";

            int dotIndex =
                    originalName.lastIndexOf('.');

            if (dotIndex >= 0) {
                extension =
                        originalName.substring(dotIndex);
            }

            String fileName =
                    UUID.randomUUID() + extension;

            Path target =
                    directory.resolve(fileName);

            Files.copy(
                    file.getInputStream(),
                    target
            );

            return publicBaseUrl
                    + "/uploads/images/"
                    + fileName;

        } catch (IOException e) {

            throw new RuntimeException(
                    "Failed to upload image",
                    e
            );
        }
    }
}