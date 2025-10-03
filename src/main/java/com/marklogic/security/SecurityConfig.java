package com.marklogic.security;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized security configuration and utilities for MLEAProxy
 * Provides security validation, input sanitization, and threat detection
 */
@Configuration
public class SecurityConfig {
    
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);
    
    // Security limits
    public static final int MAX_REQUEST_SIZE = 1024 * 1024; // 1MB
    public static final int MAX_DN_LENGTH = 1024;
    public static final int MAX_FILTER_LENGTH = 2048;
    public static final int MAX_XML_SIZE = 10 * 1024 * 1024; // 10MB
    public static final int MAX_BIND_ATTEMPTS_PER_IP = 5;
    public static final long RATE_LIMIT_WINDOW_MS = 60000; // 1 minute
    
    // Sensitive data patterns for masking
    private static final List<String> SENSITIVE_PATTERNS = Arrays.asList(
        "password", "pwd", "userPassword", "secret", "token", "key", "credential"
    );
    
    // LDAP injection patterns
    private static final List<String> LDAP_INJECTION_PATTERNS = Arrays.asList(
        "*)", ")(", "*)(", "*))", "&(|", "|(|", "!(", "\\0", "\\00", "\\2a", "\\28", "\\29"
    );
    
    // XML threat patterns
    private static final List<String> XML_THREAT_PATTERNS = Arrays.asList(
        "<!DOCTYPE", "<!ENTITY", "&lt;!DOCTYPE", "&lt;!ENTITY",
        "SYSTEM", "PUBLIC", "file://", "javascript:", "data:"
    );
    
    // Compiled patterns for better performance
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(?i)(password|pwd|secret|token|key)\\s*[=:]\\s*[^\\s,)]+", 
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Validates LDAP DN for security threats
     */
    public static boolean isValidDN(String dn) {
        if (dn == null) return true; // Allow null DN
        
        if (dn.length() > MAX_DN_LENGTH) {
            LOG.warn("DN exceeds maximum length: {}", dn.length());
            return false;
        }
        
        return !containsLDAPInjection(dn);
    }
    
    /**
     * Validates LDAP filter for security threats
     */
    public static boolean isValidFilter(String filter) {
        if (filter == null) return true; // Allow null filter
        
        if (filter.length() > MAX_FILTER_LENGTH) {
            LOG.warn("Filter exceeds maximum length: {}", filter.length());
            return false;
        }
        
        return !containsLDAPInjection(filter);
    }
    
    /**
     * Checks for potential LDAP injection patterns
     */
    public static boolean containsLDAPInjection(String input) {
        if (input == null) return false;
        
        String lowerInput = input.toLowerCase();
        for (String pattern : LDAP_INJECTION_PATTERNS) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                LOG.warn("Potential LDAP injection detected with pattern: {}", pattern);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Validates XML content for security threats
     */
    public static boolean isValidXML(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return false;
        }
        
        if (xmlContent.length() > MAX_XML_SIZE) {
            LOG.warn("XML content exceeds maximum size: {}", xmlContent.length());
            return false;
        }
        
        return !containsXMLThreats(xmlContent);
    }
    
    /**
     * Checks for XML-based security threats
     */
    public static boolean containsXMLThreats(String xmlContent) {
        if (xmlContent == null) return false;
        
        String upperContent = xmlContent.toUpperCase();
        for (String pattern : XML_THREAT_PATTERNS) {
            if (upperContent.contains(pattern.toUpperCase())) {
                LOG.warn("Potential XML threat detected with pattern: {}", pattern);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Sanitizes strings for safe logging by masking sensitive information
     */
    public static String sanitizeForLogging(String input) {
        if (input == null) return "null";
        
        // Truncate very long strings
        String sanitized = input.length() > 500 ? input.substring(0, 500) + "..." : input;
        
        // Mask sensitive patterns using regex
        sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1=***");
        
        return sanitized;
    }
    
    /**
     * Validates request size for DoS protection
     */
    public static boolean isValidRequestSize(String content) {
        if (content == null) return true;
        
        if (content.length() > MAX_REQUEST_SIZE) {
            LOG.warn("Request size exceeds maximum: {} bytes", content.length());
            return false;
        }
        
        return true;
    }
    
    /**
     * Normalizes and validates input strings
     */
    public static String normalizeInput(String input) {
        if (input == null) return null;
        
        // Remove null bytes and control characters
        return input.replaceAll("\\x00|\\x0A|\\x0D|\\x1A", "").trim();
    }
    
    /**
     * Checks if the given string contains only safe characters
     */
    public static boolean containsOnlySafeChars(String input) {
        if (input == null) return true;
        
        // Allow alphanumeric, spaces, and common safe punctuation
        return input.matches("[a-zA-Z0-9\\s\\.,;:=@\\-_]*");
    }
}