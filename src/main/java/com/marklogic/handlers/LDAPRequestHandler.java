package com.marklogic.handlers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.marklogic.configuration.ProcessorConfig;
import com.marklogic.processors.IRequestProcessor;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.ldap.protocol.AddRequestProtocolOp;
import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.protocol.BindResponseProtocolOp;
import com.unboundid.ldap.protocol.CompareRequestProtocolOp;
import com.unboundid.ldap.protocol.DeleteRequestProtocolOp;
import com.unboundid.ldap.protocol.ExtendedRequestProtocolOp;
import com.unboundid.ldap.protocol.IntermediateResponseProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.ModifyDNRequestProtocolOp;
import com.unboundid.ldap.protocol.ModifyRequestProtocolOp;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.IntermediateResponse;
import com.unboundid.ldap.sdk.IntermediateResponseListener;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.ServerSet;
import com.unboundid.util.Debug;
import com.unboundid.util.NotMutable;
import com.unboundid.util.ThreadSafety;
import com.unboundid.util.ThreadSafetyLevel;
import com.unboundid.util.Validator;


@NotMutable()
@ThreadSafety(level = ThreadSafetyLevel.COMPLETELY_THREADSAFE)
public final class LDAPRequestHandler
        extends LDAPListenerRequestHandler
        implements IntermediateResponseListener {
    /**
     * The serial version UID for this serializable class.
     */
    private static final long serialVersionUID = -8714030276701707669L;

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(LDAPRequestHandler.class);
    
    // Security constants
    private static final int MAX_REQUEST_SIZE = 1024 * 1024; // 1MB max request size
    private static final int MAX_DN_LENGTH = 1024; // Max DN length
    private static final int MAX_FILTER_LENGTH = 2048; // Max filter length
    
    // Rate limiting (simple implementation)
    private static volatile long lastRequestTime = 0;
    private static volatile int requestCount = 0;
    private static final int MAX_REQUESTS_PER_SECOND = 100;

    // The connection to the LDAP server to which requests will be forwarded.
    private final LDAPConnection ldapConnection;

    // The client connection that has been established.
    private final LDAPListenerClientConnection listenerConnection;

    // The server set that will be used to establish the connection.
    private final ServerSet serverSet;

    private final String requestProcessor;

    /**
     * Validates and sanitizes LDAP request parameters for security
     */
    private boolean isValidRequest(String dn, String filter) {
        // Check DN length
        if (dn != null && dn.length() > MAX_DN_LENGTH) {
            logger.warn("DN exceeds maximum allowed length: {}", dn.length());
            return false;
        }
        
        // Check filter length
        if (filter != null && filter.length() > MAX_FILTER_LENGTH) {
            logger.warn("Filter exceeds maximum allowed length: {}", filter.length());
            return false;
        }
        
        // Basic LDAP injection prevention
        if (dn != null && containsLDAPInjection(dn)) {
            logger.warn("Potential LDAP injection detected in DN: {}", sanitizeForLogging(dn));
            return false;
        }
        
        if (filter != null && containsLDAPInjection(filter)) {
            logger.warn("Potential LDAP injection detected in filter: {}", sanitizeForLogging(filter));
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks for potential LDAP injection patterns
     */
    private boolean containsLDAPInjection(String input) {
        if (input == null) return false;
        
        // Check for suspicious LDAP injection patterns
        String[] suspiciousPatterns = {
            "*)", ")(", "*)(", "*)))", 
            "&(|", "|(|", "!(",
            "\\0", "\\00", "\\2a", "\\28", "\\29"
        };
        
        String lowerInput = input.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Sanitizes strings for safe logging (removes sensitive information)
     */
    private String sanitizeForLogging(String input) {
        if (input == null) return "null";
        
        // Truncate long strings and remove potential sensitive data
        String sanitized = input.length() > 100 ? input.substring(0, 100) + "..." : input;
        
        // Remove common sensitive patterns
        sanitized = sanitized.replaceAll("(?i)password[=:]\\s*[^\\s,)]+", "password=***");
        sanitized = sanitized.replaceAll("(?i)pwd[=:]\\s*[^\\s,)]+", "pwd=***");
        sanitized = sanitized.replaceAll("(?i)secret[=:]\\s*[^\\s,)]+", "secret=***");
        sanitized = sanitized.replaceAll("(?i)token[=:]\\s*[^\\s,)]+", "token=***");
        
        return sanitized;
    }
    
    /**
     * Simple rate limiting check
     */
    private boolean isRateLimited() {
        long currentTime = System.currentTimeMillis();
        
        // Reset counter every second
        if (currentTime - lastRequestTime > 1000) {
            lastRequestTime = currentTime;
            requestCount = 0;
        }
        
        requestCount++;
        
        if (requestCount > MAX_REQUESTS_PER_SECOND) {
            logger.warn("Rate limit exceeded: {} requests in the last second", requestCount);
            return true;
        }
        
        return false;
    }

    /**
     * Creates a new instance of this proxy request handler that will use the
     * provided {@link ServerSet} to connect to an LDAP server.
     */
    LDAPRequestHandler(ServerSet serverSet, String auth) throws Exception {

        Validator.ensureNotNull(serverSet);
        this.serverSet = serverSet;
        this.requestProcessor = auth;
        ldapConnection = null;
        listenerConnection = null;
        logger.debug("LDAPRequestHandler constructor called.");
        logger.debug("serverSet" + serverSet);
        logger.debug("ldapConnection" + ldapConnection);
        logger.debug("listenerConnection" + listenerConnection);
    }


    /**
     * Creates a new instance of this proxy request handler with the provided
     * information.
     *
     * @param serverSet          The server that will be used to create LDAP
     *                           connections to forward any requests received.
     *                           It must not be {@code null}.
     * @param ldapConnection     The connection to the LDAP server to which
     *                           requests will be forwarded.
     * @param listenerConnection The client connection with which this request
     *                           handler is associated.
     */
    private LDAPRequestHandler(final String requestProcessor, final ServerSet serverSet,
                               final LDAPConnection ldapConnection,
                               final LDAPListenerClientConnection listenerConnection) {
        this.serverSet = serverSet;
        this.ldapConnection = ldapConnection;
        this.listenerConnection = listenerConnection;
        this.requestProcessor = requestProcessor;
        logger.debug("LDAPRequestHandler constructor called.");
        logger.debug("serverSet: {}", serverSet);
        logger.debug("ldapConnection: {}", ldapConnection);
        logger.debug("listenerConnection: {}", listenerConnection);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPRequestHandler newInstance(
            final LDAPListenerClientConnection connection)
            throws LDAPException {
        logger.debug("LDAPRequestHandler newInstance called.");
        logger.debug("serverSet: {}", serverSet);
        logger.debug("ldapConnection: {}", ldapConnection);
        logger.debug("listenerConnection: {}", listenerConnection);
        return new LDAPRequestHandler(requestProcessor, serverSet, serverSet.getConnection(),
                connection);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public void closeInstance() {
        logger.debug("closeInstance called.");
        if (ldapConnection != null) {
            try {
                ldapConnection.close();
            } catch (Exception e) {
                logger.error("Error closing LDAP connection: {}", e.getMessage(), e);
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processAddRequest(final int messageID,
                                         final AddRequestProtocolOp request,
                                         final List<Control> controls) {

        // Call Configures processor
        logger.debug("processAddRequest called.");
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for add request");
            return createErrorResponse(messageID, ResultCode.UNAVAILABLE, "Service temporarily unavailable");
        }
        LDAPMessage message = processor.processAddRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : {}", message);
        return message;
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processBindRequest(final int messageID,
                                          final BindRequestProtocolOp request,
                                          final List<Control> controls) {

        // Call Configures processor
        logger.debug("processBindRequest called.");
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for bind request");
            BindResponseProtocolOp errorResponse = new BindResponseProtocolOp(
                ResultCode.UNAVAILABLE.intValue(), null, "Service temporarily unavailable", null, null);
            return new LDAPMessage(messageID, errorResponse, Collections.emptyList());
        }
        
        try {
            LDAPMessage message = processor.processBindRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
            logger.debug("LDAP Message : {}", message);
            return message;
        } catch (Exception e) {
            logger.error("Error processing bind request: {}", e.getMessage(), e);
            BindResponseProtocolOp errorResponse = new BindResponseProtocolOp(
                ResultCode.OTHER.intValue(), null, "Internal server error", null, null);
            return new LDAPMessage(messageID, errorResponse, Collections.emptyList());
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processCompareRequest(final int messageID,
                                             final CompareRequestProtocolOp request,
                                             final List<Control> controls) {

        // Call Configures processor
        logger.debug("processCompareRequest called.");
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for compare request");
            return createErrorResponse(messageID, ResultCode.UNAVAILABLE, "Service temporarily unavailable");
        }
        LDAPMessage message = processor.processCompareRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : {}", message);
        return message;
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processDeleteRequest(final int messageID,
                                            final DeleteRequestProtocolOp request,
                                            final List<Control> controls) {

        // Call Configures processor
        logger.debug("processDeleteRequest called.");
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for delete request");
            return createErrorResponse(messageID, ResultCode.UNAVAILABLE, "Service temporarily unavailable");
        }
        LDAPMessage message = processor.processDeleteRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : {}", message);
        return message;
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processExtendedRequest(final int messageID,
                                              final ExtendedRequestProtocolOp request,
                                              final List<Control> controls) {

        // Call Configures processor
        logger.debug("processExtendedRequest called.");
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for extended request");
            return createErrorResponse(messageID, ResultCode.UNAVAILABLE, "Service temporarily unavailable");
        }
        LDAPMessage message = processor.processExtendedRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : {}", message);
        return message;

    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processModifyRequest(final int messageID,
                                            final ModifyRequestProtocolOp request,
                                            final List<Control> controls) {

        // Call Configures processor
        logger.debug("processModifyRequest called.");
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for modify request");
            return createErrorResponse(messageID, ResultCode.UNAVAILABLE, "Service temporarily unavailable");
        }
        LDAPMessage message = processor.processModifyRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : {}", message);
        return message;
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processModifyDNRequest(final int messageID,
                                              final ModifyDNRequestProtocolOp request,
                                              final List<Control> controls) {

        // Call Configures processor
        logger.debug("processModifyDNRequest called.");
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for modify DN request");
            return createErrorResponse(messageID, ResultCode.UNAVAILABLE, "Service temporarily unavailable");
        }
        LDAPMessage message = processor.processModifyDNRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : {}", message);
        return message;
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processSearchRequest(final int messageID,
                                            final SearchRequestProtocolOp request,
                                            final List<Control> controls) {

        // Call Search
        logger.debug("processSearchRequest called.");
        
        // Rate limiting check
        if (isRateLimited()) {
            logger.warn("Search request rate limited for message ID: {}", messageID);
            return createErrorResponse(messageID, ResultCode.BUSY, "Rate limit exceeded");
        }
        
        // Security validation
        if (!isValidRequest(request.getBaseDN(), request.getFilter().toString())) {
            logger.warn("Invalid search request blocked for security reasons");
            return createErrorResponse(messageID, ResultCode.INAPPROPRIATE_MATCHING, "Invalid request parameters");
        }
        
        IRequestProcessor processor = getProcessor();
        if (processor == null) {
            logger.error("No processor available for search request");
            return createErrorResponse(messageID, ResultCode.UNAVAILABLE, "Service temporarily unavailable");
        }
        
        try {
            LDAPMessage searchResult = processor.processSearchRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
            logger.debug("Search Result processed successfully for message ID: {}", messageID);
            return searchResult;
        } catch (Exception e) {
            logger.error("Error processing search request: {}", e.getMessage(), e);
            return createErrorResponse(messageID, ResultCode.OTHER, "Internal server error");
        }
    }
    
    /**
     * Helper method to create standardized error responses for search operations
     */
    private LDAPMessage createErrorResponse(int messageID, ResultCode resultCode, String message) {
        logger.debug("Creating error response: {} - {}", resultCode, message);
        
        // Create a SearchResultDone response for search operations
        com.unboundid.ldap.protocol.SearchResultDoneProtocolOp searchDone = 
            new com.unboundid.ldap.protocol.SearchResultDoneProtocolOp(
                resultCode.intValue(), 
                null, // matchedDN
                message, // diagnosticMessage
                null  // referralURLs
            );
        
        return new LDAPMessage(messageID, searchDone, Collections.emptyList());
    }


    /**
     * {@inheritDoc}
     */
    public void intermediateResponseReturned(IntermediateResponse intermediateResponse) {
        try {
            this.listenerConnection.sendIntermediateResponse(intermediateResponse.getMessageID(), new IntermediateResponseProtocolOp(intermediateResponse.getOID(), intermediateResponse.getValue()), intermediateResponse.getControls());
        } catch (LDAPException var3) {
            Debug.debugException(var3);
        }

    }

    private IRequestProcessor getProcessor() {
        logger.debug("Processor serverSet : {}", serverSet);

        if (requestProcessor == null || requestProcessor.trim().isEmpty()) {
            logger.error("Request processor name is null or empty");
            return null;
        }

        // Get Processor Class and Config Class names
        logger.debug("Processor Config : {}", requestProcessor);
        Map<String, Object> appVars = new HashMap<>();
        appVars.put("requestProcessor", requestProcessor);
        ProcessorConfig processorCfg = ConfigFactory.create(ProcessorConfig.class, appVars);

        // Create and initialize Processor
        logger.debug("Processor Class: {}", processorCfg.requestProcessorClass());
        IRequestProcessor processor = null;
        try {
            if (processorCfg.requestProcessorClass() == null || processorCfg.requestProcessorClass().trim().isEmpty()) {
                logger.error("Processor class name is null or empty");
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Class<IRequestProcessor> clazzAuth = (Class<IRequestProcessor>) Class.forName(processorCfg.requestProcessorClass());
            processor = clazzAuth.getDeclaredConstructor().newInstance();
            processor.initialize(processorCfg);
        } catch (ClassNotFoundException e) {
            logger.error("Processor class not found: {}", processorCfg.requestProcessorClass(), e);
        } catch (InstantiationException | IllegalAccessException e) {
            logger.error("Cannot instantiate processor class: {}", processorCfg.requestProcessorClass(), e);
        } catch (Exception e) {
            logger.error("Error creating processor: {}", e.getMessage(), e);
        }

        return processor;
    }

}
