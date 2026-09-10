package com.autoparts.inventory.dto;

import com.autoparts.inventory.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/** Returned by DELETE /account so the client can show "data removed on <date>". */
@Getter
@AllArgsConstructor
public class AccountDeletionResponse {
    private final AccountStatus status;
    private final Instant deletionRequestedAt;
    /** When the account and all its data are permanently purged. */
    private final Instant purgeAfter;
}
