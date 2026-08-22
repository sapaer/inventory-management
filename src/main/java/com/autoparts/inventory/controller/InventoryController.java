package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.enums.VehicleCategory;
import com.autoparts.inventory.dto.AddPartRequest;
import com.autoparts.inventory.dto.InventoryItemResponse;
import com.autoparts.inventory.dto.QuantityUpdateRequest;
import com.autoparts.inventory.dto.UpdatePartRequest;
import com.autoparts.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<InventoryItemResponse>>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<VehicleCategory> vehicle,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(inventoryService.list(userId, q, vehicle, status)));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<InventoryItemResponse>> add(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AddPartRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(inventoryService.add(userId, req)));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiEnvelope<List<InventoryItemResponse>>> lowStock(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiEnvelope.ok(inventoryService.lowStock(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<InventoryItemResponse>> get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiEnvelope.ok(inventoryService.get(userId, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiEnvelope<InventoryItemResponse>> update(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody UpdatePartRequest req
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(inventoryService.update(userId, id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiEnvelope<Void>> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        inventoryService.delete(userId, id);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @PatchMapping("/{id}/quantity")
    public ResponseEntity<ApiEnvelope<InventoryItemResponse>> quantity(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody QuantityUpdateRequest req
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(inventoryService.updateQuantity(userId, id, req)));
    }
}
