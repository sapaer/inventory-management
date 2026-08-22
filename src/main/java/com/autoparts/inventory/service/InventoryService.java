package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.enums.OnboardingStatus;
import com.autoparts.inventory.enums.VehicleCategory;
import com.autoparts.inventory.dto.AddPartRequest;
import com.autoparts.inventory.dto.InventoryItemResponse;
import com.autoparts.inventory.dto.QuantityUpdateRequest;
import com.autoparts.inventory.dto.UpdatePartRequest;
import com.autoparts.inventory.entity.InventoryItem;
import com.autoparts.inventory.entity.User;
import com.autoparts.inventory.repository.InventoryItemRepository;
import com.autoparts.inventory.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryItemRepository items;
    private final UserRepository users;
    private final NotificationService notifier;

    public InventoryService(InventoryItemRepository items, UserRepository users, NotificationService notifier) {
        this.items = items;
        this.users = users;
        this.notifier = notifier;
    }

    public List<InventoryItemResponse> list(UUID userId, String q, List<VehicleCategory> vehicles, String status) {
        List<InventoryItem> found;
        if (q != null && !q.isBlank()) {
            found = items.fullTextSearch(userId, q);
        } else {
            found = items.findByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId);
        }
        List<InventoryItemResponse> out = new ArrayList<>();
        for (InventoryItem item : found) {
            if (vehicles != null && !vehicles.isEmpty() && !vehicles.contains(item.getVehicleCategory())) {
                continue;
            }
            if (!matchesStatus(item, status)) {
                continue;
            }
            out.add(toResponse(item, null));
        }
        return out;
    }

    public InventoryItemResponse get(UUID userId, UUID itemId) {
        return toResponse(requireOwnedActive(userId, itemId), null);
    }

    @Transactional
    public InventoryItemResponse add(UUID userId, AddPartRequest req) {
        boolean dup = items.existsByUserIdAndPartNameIgnoreCaseAndActiveTrue(userId, req.getPartName());
        int minQty = req.getMinQuantity() == null ? 2 : req.getMinQuantity();
        int qty = req.getQuantity() == null ? 0 : req.getQuantity();
        InventoryItem item = new InventoryItem();
        item.setUserId(userId);
        item.setPartName(req.getPartName());
        item.setLocalName(req.getLocalName());
        item.setSpecification(req.getSpecification());
        item.setDescription(req.getDescription());
        item.setVehicleCategory(req.getVehicleCategory());
        item.setBrand(req.getBrand());
        item.setModel(req.getModel());
        item.setQuantity(qty);
        item.setMinQuantity(minQty);
        item.setSellingPrice(req.getSellingPrice());
        item.setCostPrice(req.getCostPrice());
        item.setImages(req.getImages() == null ? List.of() : req.getImages());
        item.setActive(true);
        InventoryItem saved = items.save(item);
        log.info("part added userId={} itemId={} partName={} qty={} duplicate={}", userId, saved.getId(), saved.getPartName(), qty, dup);
        markOnboardingActive(userId);
        return toResponse(saved, dup);
    }

    private void markOnboardingActive(UUID userId) {
        User user = users.findById(userId).orElse(null);
        if (user != null && user.getOnboardingStatus() != OnboardingStatus.ACTIVE) {
            user.setOnboardingStatus(OnboardingStatus.ACTIVE);
            users.save(user);
            log.info("onboarding complete userId={}", userId);
        }
    }

    @Transactional
    public InventoryItemResponse update(UUID userId, UUID itemId, UpdatePartRequest req) {
        InventoryItem item = requireOwnedActive(userId, itemId);
        if (req.getPartName() != null) item.setPartName(req.getPartName());
        if (req.getLocalName() != null) item.setLocalName(req.getLocalName());
        if (req.getSpecification() != null) item.setSpecification(req.getSpecification());
        if (req.getDescription() != null) item.setDescription(req.getDescription());
        if (req.getVehicleCategory() != null) item.setVehicleCategory(req.getVehicleCategory());
        if (req.getBrand() != null) item.setBrand(req.getBrand());
        if (req.getModel() != null) item.setModel(req.getModel());
        if (req.getMinQuantity() != null) item.setMinQuantity(req.getMinQuantity());
        if (req.getSellingPrice() != null) item.setSellingPrice(req.getSellingPrice());
        if (req.getCostPrice() != null) item.setCostPrice(req.getCostPrice());
        if (req.getImages() != null) item.setImages(req.getImages());
        return toResponse(items.save(item), null);
    }

    @Transactional
    public InventoryItemResponse updateQuantity(UUID userId, UUID itemId, QuantityUpdateRequest req) {
        InventoryItem item = requireOwnedActive(userId, itemId);
        int before = item.getQuantity();
        int after = before + req.getChange();
        if (after < 0) {
            log.warn("quantity update rejected userId={} itemId={} before={} change={}", userId, itemId, before, req.getChange());
            throw AppException.conflict("INSUFFICIENT_STOCK", "Quantity cannot go below zero");
        }
        item.setQuantity(after);
        InventoryItem saved = items.save(item);
        log.info("quantity updated userId={} itemId={} before={} after={} changeType={}", userId, itemId, before, after, req.getChangeType());
        if (after <= saved.getMinQuantity()) {
            log.info("low stock triggered userId={} itemId={} qty={} minQty={}", userId, itemId, after, saved.getMinQuantity());
            notifier.triggerLowStockCheck(saved);
        }
        return toResponse(saved, null);
    }

    @Transactional
    public void delete(UUID userId, UUID itemId) {
        InventoryItem item = items.findById(itemId)
                .filter(i -> i.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("Part not found"));
        item.setActive(false);
        items.save(item);
        log.info("part deleted userId={} itemId={}", userId, itemId);
    }

    public List<InventoryItemResponse> lowStock(UUID userId) {
        return items.findLowStockByUser(userId).stream().map(i -> toResponse(i, null)).toList();
    }

    private InventoryItem requireOwnedActive(UUID userId, UUID itemId) {
        return items.findById(itemId)
                .filter(i -> i.getUserId().equals(userId) && i.isActive())
                .orElseThrow(() -> AppException.notFound("Part not found"));
    }

    private static boolean matchesStatus(InventoryItem item, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return switch (filter.toUpperCase()) {
            case "LOW_STOCK" -> item.getQuantity() <= item.getMinQuantity();
            case "OUT_OF_STOCK" -> item.getQuantity() == 0;
            case "IN_STOCK" -> item.getQuantity() > 0;
            default -> true;
        };
    }

    private static InventoryItemResponse toResponse(InventoryItem item, Boolean dup) {
        return new InventoryItemResponse(
                item.getId(),
                item.getPartName(),
                item.getLocalName(),
                item.getSpecification(),
                item.getDescription(),
                item.getVehicleCategory(),
                item.getBrand(),
                item.getModel(),
                item.getQuantity(),
                item.getMinQuantity(),
                item.getSellingPrice(),
                item.getImages() == null ? List.of() : item.getImages(),
                item.stockStatus(),
                item.isActive(),
                dup,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
