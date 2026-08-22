package com.autoparts.inventory.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AccountSelectRequest {
    private String phoneToken;
    private UUID accountId;
}
