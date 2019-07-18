package com.marklogic.handlers;

import com.marklogic.processors.IRequestProcessor;
import com.marklogic.configuration.ProcessorConfig;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.ldap.protocol.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.*;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


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

    // The connection to the LDAP server to which requests will be forwarded.
    private final LDAPConnection ldapConnection;

    // The client connection that has been established.
    private final LDAPListenerClientConnection listenerConnection;

    // The server set that will be used to establish the connection.
    private final ServerSet serverSet;

    private final String requestProcessor;

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
        logger.debug("serverSet" + serverSet);
        logger.debug("ldapConnection" + ldapConnection);
        logger.debug("listenerConnection" + listenerConnection);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPRequestHandler newInstance(
            final LDAPListenerClientConnection connection)
            throws LDAPException {
        logger.debug("LDAPRequestHandler newInstance called.");
        logger.debug("serverSet" + serverSet);
        logger.debug("ldapConnection" + ldapConnection);
        logger.debug("listenerConnection" + listenerConnection);
        return new LDAPRequestHandler(requestProcessor, serverSet, serverSet.getConnection(),
                connection);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public void closeInstance() {
        logger.debug("closeInstance called.");
        ldapConnection.close();
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
        LDAPMessage message = processor.processAddRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : " + message);
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
        LDAPMessage message = processor.processBindRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : " + message);
        return message;
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
        LDAPMessage message = processor.processCompareRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : " + message);
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
        LDAPMessage message = processor.processDeleteRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : " + message);
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
        LDAPMessage message = processor.processExtendedRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : " + message);
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
        LDAPMessage message = processor.processModifyRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : " + message);
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
        LDAPMessage message = processor.processModifyDNRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("LDAP Message : " + message);
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
        IRequestProcessor processor = getProcessor();
        LDAPMessage searchResult = processor.processSearchRequest(messageID, request, controls, this.ldapConnection, this.listenerConnection);
        logger.debug("Search Result : " + searchResult);
        return searchResult;
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
        logger.debug("Processor serverSet : " + serverSet);

        // Get Processor Class and Config Class names
        logger.debug("Processor Config : " + requestProcessor);
        Map appVars = new HashMap();
        appVars.put("requestProcessor", requestProcessor);
        ProcessorConfig processorCfg = ConfigFactory.create(ProcessorConfig.class, appVars);

        // Create and initialize Processor
        Map cfgVars = new HashMap();
        cfgVars.put("requestProcessor", requestProcessor);
        logger.debug("Processor Class: " + processorCfg.requestProcessorClass());
        Class<IRequestProcessor> clazzAuth = null;
        IRequestProcessor processor = null;
        try {
            clazzAuth = (Class<IRequestProcessor>) Class.forName(processorCfg.requestProcessorClass());
            processor = clazzAuth.newInstance();
            processor.initialize(processorCfg);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
        }

        return processor;
    }

}
