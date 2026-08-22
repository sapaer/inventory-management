package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.dto.PresignRequest;
import com.autoparts.inventory.service.UploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/presign")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> presign(
            @AuthenticationPrincipal UUID userId,
            @RequestBody PresignRequest req
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(uploadService.presign(userId, req.getFilename(), req.getContentType())));
    }
}
