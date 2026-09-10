package com.autoparts.inventory.enums;

public enum AccountStatus {
    ACTIVE,
    DEACTIVATED,
    /** Deletion requested; row is purged after the grace period unless the user logs back in. */
    PENDING_DELETION
}
