package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.ChangeType;

public record QuantityUpdateRequest(int change, ChangeType changeType, String note) {}
