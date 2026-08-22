package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.ChangeType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuantityUpdateRequest {
    private int change;
    private ChangeType changeType;
    private String note;
}
