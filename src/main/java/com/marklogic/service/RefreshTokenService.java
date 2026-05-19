package com.marklogic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing OAuth 2.0 refresh tokens.
 * 
 * This service provides refresh token generation, validation, and rotation.
 * Tokens are stored in-memory for simplicity (Phase 4 implementation).
 * 
 * Features:
 * - Generate cryptographically secure refresh tokens
 * - Store token metadata (username, scope, expiration)
 * - Validate and consume tokens (single-use)
 * - Token rotation (old token invalidated, new token issued)
 * - Automatic expiration cleanup
 * - Configurable token lifetime
 * 
 * Security:
 * - Tokens are UUIDs with SecureRandom
 * - Single-use tokens (consumed on refresh)
 * - Automatic expiration
 * - No token reuse after refresh
 * 
 * Configuration:
 * - oauth.refresh.token.enabled: Enable refresh tokens
 * - oauth.refresh.token.expiry.seconds: Token lifetime (default 30 days)
 * - oauth.refresh.token.cleanup.interval.seconds: Cleanup interval (default 1 hour)
 * 
 * @since 2.0.0 (Phase 4)
 */
@Service
public class RefreshTokenService {
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    @Value("${oauth.refresh.token.enabled:true}")
    private boolean enabled;

    @Value("${oauth.refresh.token.expiry.seconds:2592000}") // 30 days default
    private long tokenExpirySeconds;

    @Value("${oauth.refresh.token.cleanup.interval.seconds:3600}") // 1 hour default
    private long cleanupIntervalSeconds;

    // In-memory token storage
    // Production: use Redis, database, or distributed cache
    private final Map<String, RefreshTokenData> tokenStore = new ConcurrentHashMap<>();
    
    private final SecureRandom secureRandom = new SecureRandom();
    private Timer cleanupTimer;

    /**
     * Initialize the service and start cleanup timer.
     */
    public void init() {
        if (!enabled) {
            logger.info("Refresh token service is disabled");
            return;
        }

        logger.info("Initializing refresh token service");
        logger.info("  Token expiry: {} seconds ({} days)", 
            tokenExpirySeconds, tokenExpirySeconds / 86400);
        logger.info("  Cleanup interval: {} seconds", cleanupIntervalSeconds);

        // Start periodic cleanup
        startCleanupTimer();
        
        logger.info("Refresh token service initialized");
    }

    /**
     * Start timer for periodic cleanup of expired tokens.
     */
    private void startCleanupTimer() {
        cleanupTimer = new Timer("RefreshTokenCleanup", true);
        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredTokens();
            }
        }, cleanupIntervalSeconds * 1000, cleanupIntervalSeconds * 1000);
        
        logger.debug("Started cleanup timer with interval: {} seconds", cleanupIntervalSeconds);
    }

    /**
     * Generate a new refresh token for a user.
     * 
     * @param username Username to associate with token
     * @param scope OAuth scope (roles/permissions)
     * @return Refresh token string
     */
    public String generateRefreshToken(String username, String scope) {
        if (!enabled) {
            logger.debug("Refresh tokens disabled, returning null");
            return null;
        }

        // Generate cryptographically secure token
        String token = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenExpirySeconds);

        RefreshTokenData tokenData = new RefreshTokenData(
            token,
            username,
            scope,
            now,
            expiresAt,
            false  // not consumed
        );

        tokenStore.put(token, tokenData);
        
        logger.info("Generated refresh token for user: {} (expires: {})", username, expiresAt);
        logger.debug("  Token: {}...", token.substring(0, Math.min(10, token.length())));
        logger.debug("  Scope: {}", scope);
        logger.debug("  Active tokens: {}", tokenStore.size());

        return token;
    }

    /**
     * Validate and consume a refresh token.
     * 
     * This method validates the token and marks it as consumed (single-use).
     * Token rotation: the old token is invalidated, caller must issue new token.
     * 
     * @param token Refresh token to validate
     * @return TokenValidationResult with validation status and data
     */
    public TokenValidationResult validateAndConsumeToken(String token) {
        if (!enabled) {
            return TokenValidationResult.disabled();
        }

        if (token == null || token.trim().isEmpty()) {
            logger.debug("Empty token provided");
            return TokenValidationResult.invalid("Empty token");
        }

        RefreshTokenData tokenData = tokenStore.get(token);

        if (tokenData == null) {
            logger.warn("Token not found: {}...", 
                token.substring(0, Math.min(10, token.length())));
            return TokenValidationResult.invalid("Token not found");
        }

        // Check if already consumed
        if (tokenData.consumed) {
            logger.warn("Token already consumed: {}... (user: {})", 
                token.substring(0, Math.min(10, token.length())), tokenData.username);
            return TokenValidationResult.invalid("Token already used");
        }

        // Check expiration
        if (Instant.now().isAfter(tokenData.expiresAt)) {
            logger.warn("Token expired: {}... (user: {}, expired: {})", 
                token.substring(0, Math.min(10, token.length())), 
                tokenData.username, tokenData.expiresAt);
            tokenStore.remove(token);  // Remove expired token
            return TokenValidationResult.invalid("Token expired");
        }

        // Mark as consumed (single-use token)
        tokenData.consumed = true;
        
        // Remove from store (token rotation - cannot be reused)
        tokenStore.remove(token);

        logger.info("Successfully validated and consumed refresh token for user: {}", 
            tokenData.username);
        logger.debug("  Remaining tokens: {}", tokenStore.size());

        return TokenValidationResult.valid(tokenData.username, tokenData.scope);
    }

    /**
     * Revoke a refresh token before expiration.
     * 
     * @param token Token to revoke
     * @return true if token was revoked, false if not found
     */
    public boolean revokeToken(String token) {
        if (!enabled) {
            return false;
        }

        RefreshTokenData removed = tokenStore.remove(token);
        if (removed != null) {
            logger.info("Revoked refresh token for user: {}", removed.username);
            return true;
        }
        
        logger.debug("Token not found for revocation: {}...", 
            token.substring(0, Math.min(10, token.length())));
        return false;
    }

    /**
     * Revoke all refresh tokens for a user.
     * 
     * @param username Username whose tokens should be revoked
     * @return Number of tokens revoked
     */
    public int revokeAllUserTokens(String username) {
        if (!enabled) {
            return 0;
        }

        int revokedCount = 0;
        
        Iterator<Map.Entry<String, RefreshTokenData>> iterator = tokenStore.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RefreshTokenData> entry = iterator.next();
            if (entry.getValue().username.equals(username)) {
                iterator.remove();
                revokedCount++;
            }
        }

        if (revokedCount > 0) {
            logger.info("Revoked {} refresh tokens for user: {}", revokedCount, username);
        }

        return revokedCount;
    }

    /**
     * Cleanup expired tokens from storage.
     * 
     * This runs periodically via timer and can also be called manually.
     * 
     * @return Number of tokens removed
     */
    public int cleanupExpiredTokens() {
        if (!enabled) {
            return 0;
        }

        Instant now = Instant.now();
        int removedCount = 0;

        Iterator<Map.Entry<String, RefreshTokenData>> iterator = tokenStore.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RefreshTokenData> entry = iterator.next();
            if (now.isAfter(entry.getValue().expiresAt)) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} expired refresh tokens", removedCount);
        }

        return removedCount;
    }

    /**
     * Get count of active tokens.
     * 
     * @return Number of tokens in storage
     */
    public int getActiveTokenCount() {
        return tokenStore.size();
    }

    /**
     * Check if service is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Shutdown the service and cleanup resources.
     */
    public void shutdown() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
            logger.info("Stopped refresh token cleanup timer");
        }
        
        tokenStore.clear();
        logger.info("Cleared refresh token storage");
    }

    /**
     * Internal data class for storing refresh token metadata.
     */
    private static class RefreshTokenData {
        final String token;
        final String username;
        final String scope;
        final Instant issuedAt;
        final Instant expiresAt;
        volatile boolean consumed;

        RefreshTokenData(String token, String username, String scope, 
                        Instant issuedAt, Instant expiresAt, boolean consumed) {
            this.token = token;
            this.username = username;
            this.scope = scope;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.consumed = consumed;
        }
    }

    /**
     * Result of token validation operation.
     */
    public static class TokenValidationResult {
        private final boolean valid;
        private final String username;
        private final String scope;
        private final String errorMessage;

        private TokenValidationResult(boolean valid, String username, String scope, String errorMessage) {
            this.valid = valid;
            this.username = username;
            this.scope = scope;
            this.errorMessage = errorMessage;
        }

        public static TokenValidationResult valid(String username, String scope) {
            return new TokenValidationResult(true, username, scope, null);
        }

        public static TokenValidationResult invalid(String errorMessage) {
            return new TokenValidationResult(false, null, null, errorMessage);
        }

        public static TokenValidationResult disabled() {
            return new TokenValidationResult(false, null, null, "Refresh tokens disabled");
        }

        public boolean isValid() {
            return valid;
        }

        public String getUsername() {
            return username;
        }

        public String getScope() {
            return scope;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
