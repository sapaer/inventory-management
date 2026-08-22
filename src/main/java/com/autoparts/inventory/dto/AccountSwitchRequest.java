package com.autoparts.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccountSwitchRequest {
    private UUID accountId;
}
