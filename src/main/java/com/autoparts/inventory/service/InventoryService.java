package com.autoparts.inventory.service;

import com.autoparts.inventory.api.AppException;
import com.autoparts.inventory.enums.ChangeType;
import com.autoparts.inventory.enums.VehicleCategory;
import com.autoparts.inventory.dto.AddPartRequest;
import com.autoparts.inventory.dto.QuantityUpdateRequest;
import com.autoparts.inventory.dto.UpdatePartRequest;
import com.autoparts.inventory.entity.InventoryHistory;
import com.autoparts.inventory.entity.InventoryItem;
import com.autoparts.inventory.repository.InventoryHistoryRepository;
import com.autoparts.inventory.repository.InventoryItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {
    private final InventoryItemRepository items;
    private final InventoryHistoryRepository history;
    private final NotificationService notifier;

    public InventoryService(InventoryItemRepository items, InventoryHistoryRepository history, NotificationService notifier) {
        this.items = items;
        this.history = history;
        this.notifier = notifier;
    }

    public List<Map<String, Object>> list(UUID userId, String q, List<VehicleCategory> vehicles, String status) {
        List<InventoryItem> found;
        if (q != null && !q.isBlank()) {
            found = items.fullTextSearch(userId, q);
        } else {
            found = items.findByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId);
        }
        List<Map<String, Object>> out = new ArrayList<>();
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

    public Map<String, Object> get(UUID userId, UUID itemId) {
        return toResponse(requireOwnedActive(userId, itemId), null);
    }

    @Transactional
    public Map<String, Object> add(UUID userId, AddPartRequest req) {
        boolean dup = items.existsByUserIdAndPartNameIgnoreCaseAndActiveTrue(userId, req.partName());
        int minQty = req.minQuantity() == null ? 2 : req.minQuantity();
        int qty = req.quantity() == null ? 0 : req.quantity();
        InventoryItem item = new InventoryItem();
        item.setUserId(userId);
        item.setPartName(req.partName());
        item.setLocalName(req.localName());
        item.setSpecification(req.specification());
        item.setDescription(req.description());
        item.setVehicleCategory(req.vehicleCategory());
        item.setBrand(req.brand());
        item.setModel(req.model());
        item.setQuantity(qty);
        item.setMinQuantity(minQty);
        item.setSellingPrice(req.sellingPrice());
        item.setCostPrice(req.costPrice());
        item.setImages(req.images() == null ? List.of() : req.images());
        item.setActive(true);
        InventoryItem saved = items.save(item);
        InventoryHistory h = new InventoryHistory();
        h.setItemId(saved.getId());
        h.setUserId(userId);
        h.setChangeType(ChangeType.ADD);
        h.setQtyBefore(0);
        h.setQtyChange(qty);
        h.setQtyAfter(qty);
        h.setNote("Initial stock");
        history.save(h);
        return toResponse(saved, dup);
    }

    @Transactional
    public Map<String, Object> update(UUID userId, UUID itemId, UpdatePartRequest req) {
        InventoryItem item = requireOwnedActive(userId, itemId);
        if (req.partName() != null) item.setPartName(req.partName());
        if (req.localName() != null) item.setLocalName(req.localName());
        if (req.specification() != null) item.setSpecification(req.specification());
        if (req.description() != null) item.setDescription(req.description());
        if (req.vehicleCategory() != null) item.setVehicleCategory(req.vehicleCategory());
        if (req.brand() != null) item.setBrand(req.brand());
        if (req.model() != null) item.setModel(req.model());
        if (req.minQuantity() != null) item.setMinQuantity(req.minQuantity());
        if (req.sellingPrice() != null) item.setSellingPrice(req.sellingPrice());
        if (req.costPrice() != null) item.setCostPrice(req.costPrice());
        if (req.images() != null) item.setImages(req.images());
        return toResponse(items.save(item), null);
    }

    @Transactional
    public Map<String, Object> updateQuantity(UUID userId, UUID itemId, QuantityUpdateRequest req) {
        InventoryItem item = requireOwnedActive(userId, itemId);
        int before = item.getQuantity();
        int after = before + req.change();
        if (after < 0) {
            throw AppException.conflict("INSUFFICIENT_STOCK", "Quantity cannot go below zero");
        }
        item.setQuantity(after);
        InventoryItem saved = items.save(item);
        ChangeType changeType = req.changeType() == null ? ChangeType.ADJUSTMENT : req.changeType();
        InventoryHistory h = new InventoryHistory();
        h.setItemId(itemId);
        h.setUserId(userId);
        h.setChangeType(changeType);
        h.setQtyBefore(before);
        h.setQtyChange(req.change());
        h.setQtyAfter(after);
        h.setNote(req.note());
        history.save(h);
        if (after <= saved.getMinQuantity()) {
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
    }

    public List<Map<String, Object>> lowStock(UUID userId) {
        return items.findLowStockByUser(userId).stream().map(i -> toResponse(i, null)).toList();
    }

    public Map<String, Object> history(UUID userId, UUID itemId, int page, int limit) {
        InventoryItem item = items.findById(itemId)
                .filter(i -> i.getUserId().equals(userId))
                .orElseThrow(() -> AppException.notFound("Part not found"));
        if (page < 1) page = 1;
        if (limit <= 0) limit = 20;
        Page<InventoryHistory> rows = history.findByItemIdOrderByCreatedAtDesc(item.getId(), PageRequest.of(page - 1, limit));
        List<Map<String, Object>> content = rows.getContent().stream().map(h -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", h.getId());
            dto.put("itemId", h.getItemId());
            dto.put("changeType", h.getChangeType());
            dto.put("qtyBefore", h.getQtyBefore());
            dto.put("qtyChange", h.getQtyChange());
            dto.put("qtyAfter", h.getQtyAfter());
            dto.put("note", h.getNote());
            dto.put("createdAt", h.getCreatedAt());
            return dto;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("page", page);
        out.put("limit", limit);
        out.put("total", rows.getTotalElements());
        return out;
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

    private static Map<String, Object> toResponse(InventoryItem item, Boolean dup) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("partName", item.getPartName());
        dto.put("localName", item.getLocalName());
        dto.put("specification", item.getSpecification());
        dto.put("description", item.getDescription());
        dto.put("vehicleCategory", item.getVehicleCategory());
        dto.put("brand", item.getBrand());
        dto.put("model", item.getModel());
        dto.put("quantity", item.getQuantity());
        dto.put("minQuantity", item.getMinQuantity());
        dto.put("sellingPrice", item.getSellingPrice());
        dto.put("images", item.getImages() == null ? List.of() : item.getImages());
        dto.put("stockStatus", item.stockStatus());
        dto.put("isActive", item.isActive());
        if (dup != null) {
            dto.put("isDuplicate", dup);
        }
        dto.put("createdAt", item.getCreatedAt());
        dto.put("updatedAt", item.getUpdatedAt());
        return dto;
    }
}
