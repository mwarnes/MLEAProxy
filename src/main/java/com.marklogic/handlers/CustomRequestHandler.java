package com.marklogic.handlers;

import com.marklogic.authenticators.IAuthenticator;
import com.marklogic.configuration.AuthenticatorsConfig;
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
public final class CustomRequestHandler
        extends LDAPListenerRequestHandler
        implements IntermediateResponseListener {
    /**
     * The serial version UID for this serializable class.
     */
    private static final long serialVersionUID = -8714030276701707669L;

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(CustomRequestHandler.class);

    // The connection to the LDAP server to which requests will be forwarded.
    private final LDAPConnection ldapConnection;

    // The client connection that has been established.
    private final LDAPListenerClientConnection listenerConnection;

    // The server set that will be used to establish the connection.
    private final ServerSet serverSet;

    private final String authenticator;

    /**
     * Creates a new instance of this proxy request handler that will use the
     * provided {@link ServerSet} to connect to an LDAP server.
     */
    CustomRequestHandler(ServerSet serverSet, String auth) throws Exception {

        Validator.ensureNotNull(serverSet);
        this.serverSet = serverSet;
        this.authenticator = auth;
        ldapConnection = null;
        listenerConnection = null;
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
    private CustomRequestHandler(final String authenticator, final ServerSet serverSet,
                                 final LDAPConnection ldapConnection,
                                 final LDAPListenerClientConnection listenerConnection) {
        this.serverSet = serverSet;
        this.ldapConnection = ldapConnection;
        this.listenerConnection = listenerConnection;
        this.authenticator = authenticator;
        logger.debug("CustomRequestHandler constructor called.");
        logger.debug("serverSet" + serverSet);
        logger.debug("ldapConnection" + ldapConnection);
        logger.debug("listenerConnection" + listenerConnection);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public CustomRequestHandler newInstance(
            final LDAPListenerClientConnection connection)
            throws LDAPException {
        logger.debug("CustomRequestHandler newInstance called.");
        logger.debug("serverSet" + serverSet);
        logger.debug("ldapConnection" + ldapConnection);
        logger.debug("listenerConnection" + listenerConnection);
        return new CustomRequestHandler(authenticator, serverSet, serverSet.getConnection(),
                connection);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public void closeInstance() {
        ldapConnection.close();
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processAddRequest(final int messageID,
                                         final AddRequestProtocolOp request,
                                         final List<Control> controls) {
        logger.error("LDAP ADD request not accepted by CustomRequestHandler.");
        logger.debug("LDAP Request: " + request);

        final AddResponseProtocolOp addResponseProtocolOp =
                new AddResponseProtocolOp(53,
                        null, null,
                        null);
        logger.debug("LDAP Response: " + addResponseProtocolOp);
        return new LDAPMessage(messageID, addResponseProtocolOp,
                controls);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processBindRequest(final int messageID,
                                          final BindRequestProtocolOp request,
                                          final List<Control> controls) {
        logger.debug("LDAP Request: " + request);
        logger.debug("LDAP MessageID: " + messageID);

        logger.debug("Authenticator serverSet : " + serverSet);

        // Get Authenticator Class and Config Class names
        logger.debug("Authenticator Config : " + authenticator);
        Map appVars = new HashMap();
        appVars.put("authenticator", authenticator);
        AuthenticatorsConfig appCfg = ConfigFactory.create(AuthenticatorsConfig.class, appVars);
        logger.debug(appCfg.toString());

        final Control[] controlArray;
        if ((controls == null) || (controls.isEmpty())) {
            controlArray = StaticUtils.NO_CONTROLS;
        } else {
            controlArray = new Control[controls.size()];
            controls.toArray(controlArray);
        }

        // Create Authenticator configuration class
        logger.debug("Authenticator Class: " + appCfg.authenticatorClass());
        logger.debug("Authenticator Config Class: " + appCfg.authenticatorConfigClass());
        Class<Config> clazzConfig = null;
        try {
            clazzConfig = (Class<Config>) Class.forName(appCfg.authenticatorConfigClass());
        } catch (ClassNotFoundException e) {
            logger.error(e.getLocalizedMessage());
        }

        // Create and initialize Authenticator
        Map cfgVars = new HashMap();
        cfgVars.put("authenticator", authenticator);
        Config authCfg = ConfigFactory.create(clazzConfig, cfgVars);
        logger.debug("Authenticator Class: " + appCfg.authenticatorClass());
        Class<IAuthenticator> clazzAuth = null;
        IAuthenticator authenticator = null;
        try {
            clazzAuth = (Class<IAuthenticator>) Class.forName(appCfg.authenticatorClass());
            authenticator = clazzAuth.newInstance();
            authenticator.initialize(authCfg);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
        }

        // Call Authenticator for Bind
        logger.debug("Calling Authenticator " + authenticator);
        logger.debug("BindRequestProtocolOp: " + request);
        LDAPResult bindResult = authenticator.authenticate(request, null, null);

        // Return Bind response
        final BindResponseProtocolOp bindResponseProtocolOp =
                new BindResponseProtocolOp(bindResult.getResultCode().intValue(),
                        bindResult.getMatchedDN(), bindResult.getDiagnosticMessage(),
                        Arrays.asList(bindResult.getReferralURLs()), null);

        logger.info(bindResponseProtocolOp.toString());
        return new LDAPMessage(messageID, bindResponseProtocolOp,
                Arrays.asList(bindResult.getResponseControls()));
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processCompareRequest(final int messageID,
                                             final CompareRequestProtocolOp request,
                                             final List<Control> controls) {
        logger.error("LDAP COMPARE request not accepted by CustomRequestHandler.");
        logger.debug("LDAP Request: " + request);

        final CompareResponseProtocolOp compareResponseProtocolOp =
                new CompareResponseProtocolOp(53,
                        null,
                        null,
                        null);

        logger.debug("LDAP Response: " + compareResponseProtocolOp);
        return new LDAPMessage(messageID, compareResponseProtocolOp,
                controls);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processDeleteRequest(final int messageID,
                                            final DeleteRequestProtocolOp request,
                                            final List<Control> controls) {
        logger.error("LDAP DELETE request not accepted by CustomRequestHandler.");
        logger.debug("LDAP Request: " + request);

        final DeleteResponseProtocolOp deleteResponseProtocolOp =
                new DeleteResponseProtocolOp(53,
                        null, null,
                        null);

        logger.debug("LDAP Response: " + deleteResponseProtocolOp);
        return new LDAPMessage(messageID, deleteResponseProtocolOp,
                controls);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processExtendedRequest(final int messageID,
                                              final ExtendedRequestProtocolOp request,
                                              final List<Control> controls) {
        logger.error("LDAP EXTENDED request not accepted by CustomRequestHandler.");
        logger.debug("LDAP Request: " + request);

        final ExtendedResponseProtocolOp extendedResponseProtocolOp =
                new ExtendedResponseProtocolOp(53,
                        null, null,
                        null, null, null);

        logger.debug("LDAP Response: " + extendedResponseProtocolOp);
        return new LDAPMessage(messageID, extendedResponseProtocolOp,
                controls);

    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processModifyRequest(final int messageID,
                                            final ModifyRequestProtocolOp request,
                                            final List<Control> controls) {
        logger.error("LDAP MODIFY request not accepted by CustomRequestHandler.");
        logger.debug("LDAP Request: " + request);

        final ModifyResponseProtocolOp modifyResponseProtocolOp =
                new ModifyResponseProtocolOp(53,
                        null, null,
                        null);

        logger.debug("LDAP Response: " + modifyResponseProtocolOp);
        return new LDAPMessage(messageID, modifyResponseProtocolOp,
                controls);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processModifyDNRequest(final int messageID,
                                              final ModifyDNRequestProtocolOp request,
                                              final List<Control> controls) {
        logger.error("LDAP MODIFYDN request not accepted by CustomRequestHandler.");
        logger.debug("LDAP Request: " + request);

        final ModifyDNResponseProtocolOp modifyDNResponseProtocolOp =
                new ModifyDNResponseProtocolOp(
                        53,
                        null,
                        null,
                        null);

        logger.debug("LDAP Response: " + modifyDNResponseProtocolOp);
        return new LDAPMessage(messageID, modifyDNResponseProtocolOp,
                controls);
    }


    /**
     * {@inheritDoc}
     */
    @Override()
    public LDAPMessage processSearchRequest(final int messageID,
                                            final SearchRequestProtocolOp request,
                                            final List<Control> controls) {
        logger.debug("LDAP Request: " + request);
        logger.debug("LDAP MessageId: " + messageID);
        final String[] attrs;
        final List<String> attrList = request.getAttributes();
        if (attrList.isEmpty()) {
            attrs = StaticUtils.NO_STRINGS;
        } else {
            attrs = new String[attrList.size()];
            attrList.toArray(attrs);
        }

        // Get Authenticator Class and Config Class names
        logger.debug("Authenticator Config : " + authenticator);
        Map appVars = new HashMap();
        appVars.put("authenticator", authenticator);
        AuthenticatorsConfig appCfg = ConfigFactory.create(AuthenticatorsConfig.class, appVars);
        logger.debug(appCfg.toString());

        final Control[] controlArray;
        if ((controls == null) || (controls.isEmpty())) {
            controlArray = StaticUtils.NO_CONTROLS;
        } else {
            controlArray = new Control[controls.size()];
            controls.toArray(controlArray);
        }

        // Create Authenticator configuration class
        logger.debug("Authenticator Class: " + appCfg.authenticatorClass());
        logger.debug("Authenticator Config Class: " + appCfg.authenticatorConfigClass());
        Class<Config> clazzConfig = null;
        try {
            clazzConfig = (Class<Config>) Class.forName(appCfg.authenticatorConfigClass());
        } catch (ClassNotFoundException e) {
            logger.error(e.getLocalizedMessage());
        }

        // Create and initialize Authenticator
        Map cfgVars = new HashMap();
        cfgVars.put("authenticator", authenticator);
        Config authCfg = ConfigFactory.create(clazzConfig, cfgVars);
        logger.debug("Authenticator Class: " + appCfg.authenticatorClass());
        Class<IAuthenticator> clazzAuth = null;
        IAuthenticator authenticator = null;
        try {
            clazzAuth = (Class<IAuthenticator>) Class.forName(appCfg.authenticatorClass());
            authenticator = clazzAuth.newInstance();
            authenticator.initialize(authCfg);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
        }

        // Call Authenticator to perform Search
        logger.debug("Calling Authenticator " + authenticator);
        logger.debug("SearchRequestProtocolOp: " + request);

        // Create listener to handle any intermediate response
        SearchResultListener searchListener = new SearchResultListener(this.listenerConnection, messageID);

        //Create SearchRequest
        SearchRequest searchRequest = new SearchRequest(searchListener, request.getBaseDN(), request.getScope(), request.getDerefPolicy(), request.getSizeLimit(), request.getTimeLimit(), request.typesOnly(), request.getFilter(), attrs);
        if(!controls.isEmpty()) {
            searchRequest.setControls(controls);
        }
        searchRequest.setIntermediateResponseListener(this);

        // Call Search
        SearchResult searchResult;
        searchResult = authenticator.search(messageID, searchRequest, this.listenerConnection);

        // Return result
        SearchResultDoneProtocolOp searchResultDoneProtocolOp = new SearchResultDoneProtocolOp(((LDAPResult)searchResult).getResultCode().intValue(), ((LDAPResult)searchResult).getMatchedDN(), ((LDAPResult)searchResult).getDiagnosticMessage(), Arrays.asList(((LDAPResult)searchResult).getReferralURLs()));
        logger.info(searchResultDoneProtocolOp.toString());
        return new LDAPMessage(messageID, searchResultDoneProtocolOp, Arrays.asList(((LDAPResult)searchResult).getResponseControls()));
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

}
