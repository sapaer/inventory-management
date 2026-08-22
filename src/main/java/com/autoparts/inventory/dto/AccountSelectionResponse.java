package com.autoparts.inventory.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class AccountSelectionResponse implements AuthResult {
    private final boolean needsAccountSelection = true;
    private final String phoneToken;
    private final List<AccountSummaryResponse> accounts;

    public AccountSelectionResponse(String phoneToken, List<AccountSummaryResponse> accounts) {
        this.phoneToken = phoneToken;
        this.accounts = accounts;
    }
}
