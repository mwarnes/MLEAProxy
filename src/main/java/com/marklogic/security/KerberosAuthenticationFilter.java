package com.marklogic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ietf.jgss.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Kerberos/SPNEGO authentication filter using JAAS and GSS-API.
 * 
 * This filter handles Kerberos ticket validation without relying on
 * the deprecated Spring Security Kerberos library. Instead, it uses:
 * - Java's built-in JAAS (Java Authentication and Authorization Service)
 * - GSS-API (Generic Security Services API) for SPNEGO
 * 
 * Flow:
 * 1. Check for Authorization: Negotiate header
 * 2. If missing, return 401 with WWW-Authenticate: Negotiate
 * 3. If present, decode the base64 ticket
 * 4. Use GSS-API to validate the ticket
 * 5. Extract principal name from validated ticket
 * 6. Create Spring Security authentication and set in context
 * 
 * Configuration:
 * - servicePrincipal: Service principal (e.g., HTTP/localhost@MARKLOGIC.LOCAL)
 * - keytabLocation: Path to keytab file
 * - debug: Enable JAAS debug logging
 * 
 * @see org.ietf.jgss.GSSContext
 * @see javax.security.auth.login.LoginContext
 * @since 2.0.0
 */
public class KerberosAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(KerberosAuthenticationFilter.class);
    
    private static final String NEGOTIATE_HEADER = "Negotiate";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";

    private final String servicePrincipal;
    private final String keytabLocation;
    private final boolean debug;

    /**
     * Create a Kerberos authentication filter.
     * 
     * @param servicePrincipal Service principal (e.g., HTTP/localhost@MARKLOGIC.LOCAL)
     * @param keytabLocation Path to keytab file
     * @param debug Enable JAAS debug logging
     */
    public KerberosAuthenticationFilter(String servicePrincipal, String keytabLocation, boolean debug) {
        this.servicePrincipal = servicePrincipal;
        this.keytabLocation = keytabLocation;
        this.debug = debug;
        
        // Set JAAS debug system property if enabled
        if (debug) {
            System.setProperty("sun.security.krb5.debug", "true");
            System.setProperty("sun.security.spnego.debug", "true");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        // Skip authentication for health check endpoint
        if (request.getRequestURI().equals("/kerberos/health")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        // If no Authorization header or not Negotiate, challenge client
        if (authHeader == null || !authHeader.startsWith(NEGOTIATE_HEADER + " ")) {
            if (logger.isDebugEnabled()) {
                logger.debug("No Kerberos ticket found, sending WWW-Authenticate: Negotiate challenge");
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(WWW_AUTHENTICATE_HEADER, NEGOTIATE_HEADER);
            return;
        }

        // Extract and validate Kerberos ticket
        try {
            String base64Token = authHeader.substring(NEGOTIATE_HEADER.length() + 1);
            byte[] kerberosTicket = Base64.getDecoder().decode(base64Token);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Received Kerberos ticket, size: {} bytes", kerberosTicket.length);
            }

            // Validate ticket and extract principal
            String principal = validateTicketAndGetPrincipal(kerberosTicket);
            
            if (principal != null) {
                logger.info("Successfully authenticated user: {}", principal);
                
                // Create Spring Security authentication
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                // Continue filter chain
                chain.doFilter(request, response);
            } else {
                logger.warn("Kerberos ticket validation failed");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader(WWW_AUTHENTICATE_HEADER, NEGOTIATE_HEADER);
            }
            
        } catch (Exception e) {
            logger.error("Error processing Kerberos ticket", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(WWW_AUTHENTICATE_HEADER, NEGOTIATE_HEADER);
        }
    }

    /**
     * Validate Kerberos ticket using GSS-API and extract principal name.
     * 
     * This method:
     * 1. Creates a JAAS login context using the service principal and keytab
     * 2. Creates a GSS context for SPNEGO
     * 3. Accepts the security context using the client's ticket
     * 4. Extracts and returns the principal name
     * 
     * @param kerberosTicket Base64-decoded Kerberos ticket
     * @return Principal name (e.g., "mluser1@MARKLOGIC.LOCAL") or null if validation fails
     */
    private String validateTicketAndGetPrincipal(byte[] kerberosTicket) {
        try {
            // Create JAAS configuration
            javax.security.auth.login.Configuration jaasConfig = createJaasConfiguration();
            javax.security.auth.login.Configuration.setConfiguration(jaasConfig);

            // Login as service principal using keytab
            LoginContext loginContext = new LoginContext("KerberosService");
            loginContext.login();
            
            Subject serviceSubject = loginContext.getSubject();
            if (logger.isDebugEnabled()) {
                logger.debug("Service login successful, principals: {}", serviceSubject.getPrincipals());
            }

            // Validate ticket using GSS-API within privileged context
            String principal;
            try {
                principal = Subject.callAs(serviceSubject, () -> {
                    // Create GSS context
                    GSSManager manager = GSSManager.getInstance();
                    GSSContext context = manager.createContext((GSSCredential) null);

                    // Accept security context (validate ticket)
                    context.acceptSecContext(kerberosTicket, 0, kerberosTicket.length);

                    // Extract client principal
                    String clientPrincipal = context.getSrcName().toString();
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("Ticket validated successfully");
                        logger.debug("  Client principal: {}", clientPrincipal);
                        logger.debug("  Service principal: {}", context.getTargName());
                        logger.debug("  Context established: {}", context.isEstablished());
                    }

                    // Clean up
                    context.dispose();
                    
                    return clientPrincipal;
                });
            } catch (Exception e) {
                logger.error("Error validating Kerberos ticket", e);
                principal = null;
            }

            // Logout
            loginContext.logout();
            
            return principal;
            
        } catch (LoginException e) {
            logger.error("JAAS login failed for service principal: {}", servicePrincipal, e);
            return null;
        }
    }

    /**
     * Create JAAS configuration for service principal authentication.
     * 
     * This configuration tells JAAS to:
     * - Use Krb5LoginModule (Kerberos v5)
     * - Use keytab authentication (not password)
     * - Specify the service principal and keytab location
     * 
     * @return JAAS configuration
     */
    private javax.security.auth.login.Configuration createJaasConfiguration() {
        return new javax.security.auth.login.Configuration() {
            @Override
            public javax.security.auth.login.AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                Map<String, String> options = new HashMap<>();
                options.put("useKeyTab", "true");
                options.put("storeKey", "true");
                options.put("doNotPrompt", "true");
                options.put("isInitiator", "false");  // We're the acceptor (server)
                options.put("principal", servicePrincipal);
                options.put("keyTab", new File(keytabLocation).getAbsolutePath());
                
                if (debug) {
                    options.put("debug", "true");
                }

                return new javax.security.auth.login.AppConfigurationEntry[] {
                    new javax.security.auth.login.AppConfigurationEntry(
                        "com.sun.security.auth.module.Krb5LoginModule",
                        javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options
                    )
                };
            }
        };
    }
}
