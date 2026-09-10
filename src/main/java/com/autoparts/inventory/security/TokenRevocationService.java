package com.autoparts.inventory.security;

import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.store.AppKvStore;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Immediate lockout for already-issued access tokens. JWTs are stateless and live ~24h, so
 * clearing the refresh session alone lets a deactivated / pending-deletion account keep
 * calling the API until its access token expires. A marker in {@link AppKvStore} closes that
 * window: {@link JwtAuthFilter} rejects any token whose account has one.
 *
 * <p>The marker only needs to outlive the longest-lived access token, so it carries a TTL of
 * {@code accessExpiryHours + 1} and then self-expires — by which point every token issued
 * before the revocation is already dead.
 */
@Service
public class TokenRevocationService {
    private static final String PREFIX = "revoked_access:";

    private final AppKvStore cache;
    private final Duration ttl;

    public TokenRevocationService(AppKvStore cache, AppProperties props) {
        this.cache = cache;
        this.ttl = Duration.ofHours(props.getJwt().getAccessExpiryHours() + 1L);
    }

    /** Reject every access token already issued for this account. */
    public void revoke(UUID userId) {
        cache.set(PREFIX + userId, "1", ttl);
    }

    /** Account is active again — let freshly issued tokens through. */
    public void restore(UUID userId) {
        cache.delete(PREFIX + userId);
    }

    public boolean isRevoked(UUID userId) {
        return cache.existsActive(PREFIX + userId);
    }
}
