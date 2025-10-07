package com.marklogic.configuration;

import com.marklogic.security.KerberosAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.File;

/**
 * Spring Security configuration for Kerberos/SPNEGO authentication.
 * 
 * This configuration enables SPNEGO (Simple and Protected GSSAPI Negotiation Mechanism)
 * authentication for HTTP endpoints under /kerberos/*.
 * 
 * How it works:
 * 1. Browser sends request to /kerberos/auth
 * 2. Server responds with WWW-Authenticate: Negotiate (if no ticket provided)
 * 3. Browser sends Kerberos ticket in Authorization: Negotiate header
 * 4. Server validates ticket using JAAS/GSS-API
 * 5. Server returns JWT token for authenticated user
 * 
 * Configuration:
 * - kerberos.enabled: Enable/disable Kerberos authentication
 * - kerberos.service-principal: Service principal (e.g., HTTP/localhost@MARKLOGIC.LOCAL)
 * - kerberos.keytab-location: Path to keytab file
 * - kerberos.debug: Enable JAAS debug logging
 * 
 * @since 2.0.0
 */
@Configuration
@EnableWebSecurity
public class KerberosSecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(KerberosSecurityConfig.class);

    @Value("${kerberos.enabled:false}")
    private boolean kerberosEnabled;

    @Value("${kerberos.service-principal:HTTP/localhost@MARKLOGIC.LOCAL}")
    private String servicePrincipal;

    @Value("${kerberos.keytab-location:./kerberos/keytabs/service.keytab}")
    private String keytabLocation;

    @Value("${kerberos.debug:false}")
    private boolean debug;

    /**
     * Configure HTTP security with Kerberos/SPNEGO authentication.
     * 
     * Security rules:
     * - /kerberos/health - Public endpoint (no authentication)
     * - /kerberos/** - Requires Kerberos authentication
     * - All other endpoints - Not affected by this configuration
     * - CSRF disabled for /kerberos/** (stateless API)
     * 
     * @param http HttpSecurity configuration
     * @return Security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain kerberosFilterChain(HttpSecurity http) throws Exception {
        if (!kerberosEnabled) {
            logger.debug("Kerberos SPNEGO authentication is disabled");
            // Allow all requests to /kerberos/** when Kerberos is disabled
            http
                .securityMatcher("/kerberos/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
            return http.build();
        }

        logger.info("Configuring Kerberos SPNEGO authentication");
        logger.info("  Service Principal: {}", servicePrincipal);
        logger.info("  Keytab Location: {}", keytabLocation);

        // Validate keytab file exists
        File keytabFile = new File(keytabLocation);
        if (!keytabFile.exists()) {
            logger.warn("Keytab file not found: {}", keytabLocation);
            logger.warn("Kerberos authentication will fail. Start the KDC first to generate keytabs.");
        } else {
            logger.info("  Keytab file found: {}", keytabFile.getAbsolutePath());
        }

        // Create Kerberos authentication filter
        KerberosAuthenticationFilter kerberosFilter = new KerberosAuthenticationFilter(
            servicePrincipal,
            keytabLocation,
            debug
        );

        http
            .securityMatcher("/kerberos/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/kerberos/health").permitAll()  // Health check doesn't require auth
                .anyRequest().authenticated()
            )
            .addFilterBefore(kerberosFilter, BasicAuthenticationFilter.class)
            .csrf(csrf -> csrf.disable());  // Disable CSRF for stateless API

        return http.build();
    }
}

