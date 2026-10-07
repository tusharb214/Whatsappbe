package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /**
     * Common image upload.
     *
     * Used by:
     * - Flow Builder
     * - Catalog
     * - Product
     * - Future image-based features
     */
    @PostMapping(
            value = "/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {

        String url =
                assetService.uploadImage(file);

        return ResponseEntity.ok(
                Map.of("url", url)
        );
    }
}