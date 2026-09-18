package com.marklogic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for OAuth 2.0 authorization codes.
 *
 * <p>Codes are opaque, single-use and short-lived, which is what a real Authorization
 * Server does and therefore what MLEAProxy should demonstrate. A self-contained signed
 * code would have avoided the bookkeeping, but then replay could not be detected, and
 * showing a replayed code being rejected has teaching value.
 *
 * <p>Expired entries are evicted lazily on access and opportunistically on issue, which is
 * sufficient for a development tool: the volume is low and the TTL is seconds.
 *
 * @since 2.0.4
 */
@Service
public class AuthorizationCodeStore {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationCodeStore.class);

    private static final int CODE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, AuthorizationCode> codes = new ConcurrentHashMap<>();

    @Value("${oauth.authorization-code.ttl-seconds:60}")
    private int ttlSeconds = 60;

    /**
     * Details captured at the authorize step and needed again at the token step.
     *
     * @param clientId          client that requested the code
     * @param redirectUri       redirect URI the code was issued for, or null if none was sent
     * @param scope             requested scope, possibly empty
     * @param codeChallenge     PKCE challenge, or null when PKCE was not used
     * @param codeChallengeMethod PKCE method (S256 or plain), or null
     * @param username          username the operator chose at the login page
     * @param roles             roles the operator chose
     * @param tokenLifetimeSeconds lifetime to apply to the issued access token
     * @param issuedAt          issue time, used for expiry
     */
    public record AuthorizationCode(
            String clientId,
            String redirectUri,
            String scope,
            String codeChallenge,
            String codeChallengeMethod,
            String username,
            List<String> roles,
            Integer tokenLifetimeSeconds,
            Instant issuedAt) {
    }

    /**
     * Issues a new authorization code.
     *
     * @return the opaque code value to hand back to the client
     */
    public String issue(AuthorizationCode details) {
        evictExpired();

        byte[] raw = new byte[CODE_BYTES];
        random.nextBytes(raw);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        codes.put(code, details);
        logger.info("Issued authorization code for client '{}' user '{}' (expires in {}s)",
                details.clientId(), details.username(), ttlSeconds);
        return code;
    }

    /**
     * Redeems a code, removing it so that a second attempt fails.
     *
     * <p>The removal happens before any validation so that a replayed code cannot be used
     * even if the first redemption failed for another reason.
     *
     * @return the stored details, or empty when the code is unknown, already used or expired
     */
    public Optional<AuthorizationCode> redeem(String code) {
        if (code == null || code.isEmpty()) {
            return Optional.empty();
        }

        AuthorizationCode details = codes.remove(code);
        if (details == null) {
            logger.warn("Authorization code rejected: unknown or already redeemed");
            return Optional.empty();
        }

        if (isExpired(details)) {
            logger.warn("Authorization code rejected: expired (issued at {}, ttl {}s)",
                    details.issuedAt(), ttlSeconds);
            return Optional.empty();
        }

        return Optional.of(details);
    }

    /** Lifetime applied to issued codes, in seconds. */
    public int getTtlSeconds() {
        return ttlSeconds;
    }

    /** Number of codes currently outstanding. Intended for tests and diagnostics. */
    public int outstandingCount() {
        evictExpired();
        return codes.size();
    }

    private boolean isExpired(AuthorizationCode details) {
        return details.issuedAt().plus(Duration.ofSeconds(ttlSeconds)).isBefore(Instant.now());
    }

    private void evictExpired() {
        codes.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }
}
