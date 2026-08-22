package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.AccountStatus;
import com.autoparts.inventory.enums.BusinessType;
import com.autoparts.inventory.enums.OnboardingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountSummaryResponse {
    private final UUID id;
    private final String shopName;
    private final String name;
    private final BusinessType businessType;
    private final OnboardingStatus onboardingStatus;
    private final AccountStatus status;
}
