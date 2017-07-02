package com.marklogic.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.configuration.ProcessorConfig;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.Debug;
import com.unboundid.util.StaticUtils;
import org.aeonbits.owner.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mwarnes on 05/02/2017.
 */
public class ProxyRequestProcessor implements IRequestProcessor, IntermediateResponseListener {

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(ProxyRequestProcessor.class);

    private ProcessorConfig appCfg;
    private LDAPListenerClientConnection listenerConnection;

    @Override
    public void initialize(Config cfg) throws Exception {
        this.appCfg = (ProcessorConfig) cfg;
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(ProxyRequestProcessor.class).setLevel(Level.valueOf(((ProcessorConfig) cfg).debugLevel()));
    }

    @Override
    public LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final Control[] controlArray;
        if ((controls == null) || (controls.isEmpty())) {
            controlArray = StaticUtils.NO_CONTROLS;
        } else {
            controlArray = new Control[controls.size()];
            controls.toArray(controlArray);
        }

        final BindRequest bindRequest;
        if (request.getCredentialsType() == BindRequestProtocolOp.CRED_TYPE_SIMPLE) {
            bindRequest = new SimpleBindRequest(request.getBindDN(),
                    request.getSimplePassword().getValue(), controlArray);
        } else {
            bindRequest = new GenericSASLBindRequest(request.getBindDN(),
                    request.getSASLMechanism(), request.getSASLCredentials(),
                    controlArray);
        }

        bindRequest.setIntermediateResponseListener(this);

        LDAPResult bindResult;
        try {
            bindResult = ldapConnection.bind(bindRequest);
        } catch (final LDAPException le) {
            Debug.debugException(le);
            bindResult = le.toLDAPResult();
        }

        final BindResponseProtocolOp bindResponseProtocolOp =
                new BindResponseProtocolOp(bindResult.getResultCode().intValue(),
                        bindResult.getMatchedDN(), bindResult.getDiagnosticMessage(),
                        Arrays.asList(bindResult.getReferralURLs()), null);

        logger.debug(messageID + "-+-" + bindResponseProtocolOp + "-+-" + Arrays.asList(bindResult.getResponseControls()));
        return new LDAPMessage(messageID, bindResponseProtocolOp,
                Arrays.asList(bindResult.getResponseControls()));
    }

    @Override
    public LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final String[] attrs;
        final List<String> attrList = request.getAttributes();
        if (attrList.isEmpty()) {
            attrs = StaticUtils.NO_STRINGS;
        } else {
            attrs = new String[attrList.size()];
            attrList.toArray(attrs);
        }

        final SearchResultListener searchListener =
                new SearchResultListener(listenerConnection, messageID);

        final SearchRequest searchRequest = new SearchRequest(searchListener,
                request.getBaseDN(), request.getScope(), request.getDerefPolicy(),
                request.getSizeLimit(), request.getTimeLimit(), request.typesOnly(),
                request.getFilter(), attrs);

        if (!controls.isEmpty()) {
            searchRequest.setControls(controls);
        }
        searchRequest.setIntermediateResponseListener(this);

        LDAPResult searchResult;
        try {
            searchResult = ldapConnection.search(searchRequest);
        } catch (final LDAPException le) {
            Debug.debugException(le);
            searchResult = le.toLDAPResult();
        }
        logger.debug(messageID + "-+-" + searchResult + "-+-");

        final SearchResultDoneProtocolOp searchResultDoneProtocolOp =
                new SearchResultDoneProtocolOp(searchResult.getResultCode().intValue(),
                        searchResult.getMatchedDN(), searchResult.getDiagnosticMessage(),
                        Arrays.asList(searchResult.getReferralURLs()));

        logger.debug(messageID + "-+-" + searchResultDoneProtocolOp + "-+-" + Arrays.asList(searchResult.getResponseControls()));
        return new LDAPMessage(messageID, searchResultDoneProtocolOp,
                Arrays.asList(searchResult.getResponseControls()));
    }

    @Override
    public LDAPMessage processAddRequest(int messageID, AddRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final AddRequest addRequest = new AddRequest(request.getDN(),
                request.getAttributes());
        if (!controls.isEmpty()) {
            addRequest.setControls(controls);
        }
        addRequest.setIntermediateResponseListener(this);

        LDAPResult addResult;
        try {
            addResult = ldapConnection.add(addRequest);
        } catch (final LDAPException le) {
            Debug.debugException(le);
            addResult = le.toLDAPResult();
        }

        final AddResponseProtocolOp addResponseProtocolOp =
                new AddResponseProtocolOp(addResult.getResultCode().intValue(),
                        addResult.getMatchedDN(), addResult.getDiagnosticMessage(),
                        Arrays.asList(addResult.getReferralURLs()));

        logger.debug(messageID + "-+-" + addResponseProtocolOp + "-+-" + Arrays.asList(addResult.getResponseControls()));
        return new LDAPMessage(messageID, addResponseProtocolOp,
                Arrays.asList(addResult.getResponseControls()));
    }

    @Override
    public LDAPMessage processCompareRequest(int messageID, CompareRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final CompareRequest compareRequest = new CompareRequest(request.getDN(),
                request.getAttributeName(), request.getAssertionValue().getValue());
        if (!controls.isEmpty()) {
            compareRequest.setControls(controls);
        }
        compareRequest.setIntermediateResponseListener(this);

        LDAPResult compareResult;
        try {
            compareResult = ldapConnection.compare(compareRequest);
        } catch (final LDAPException le) {
            Debug.debugException(le);
            compareResult = le.toLDAPResult();
        }

        final CompareResponseProtocolOp compareResponseProtocolOp =
                new CompareResponseProtocolOp(compareResult.getResultCode().intValue(),
                        compareResult.getMatchedDN(),
                        compareResult.getDiagnosticMessage(),
                        Arrays.asList(compareResult.getReferralURLs()));

        logger.debug(messageID + "-+-" + compareResponseProtocolOp + "-+-" + Arrays.asList(compareResult.getResponseControls()));
        return new LDAPMessage(messageID, compareResponseProtocolOp,
                Arrays.asList(compareResult.getResponseControls()));
    }

    @Override
    public LDAPMessage processDeleteRequest(int messageID, DeleteRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final DeleteRequest deleteRequest = new DeleteRequest(request.getDN());
        if (!controls.isEmpty()) {
            deleteRequest.setControls(controls);
        }
        deleteRequest.setIntermediateResponseListener(this);

        LDAPResult deleteResult;
        try {
            deleteResult = ldapConnection.delete(deleteRequest);
        } catch (final LDAPException le) {
            Debug.debugException(le);
            deleteResult = le.toLDAPResult();
        }

        final DeleteResponseProtocolOp deleteResponseProtocolOp =
                new DeleteResponseProtocolOp(deleteResult.getResultCode().intValue(),
                        deleteResult.getMatchedDN(), deleteResult.getDiagnosticMessage(),
                        Arrays.asList(deleteResult.getReferralURLs()));

        logger.debug(messageID + "-+-" + deleteResponseProtocolOp + "-+-" + Arrays.asList(deleteResult.getResponseControls()));
        return new LDAPMessage(messageID, deleteResponseProtocolOp,
                Arrays.asList(deleteResult.getResponseControls()));
    }

    @Override
    public LDAPMessage processExtendedRequest(int messageID, ExtendedRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final ExtendedRequest extendedRequest;
        if (controls.isEmpty()) {
            extendedRequest = new ExtendedRequest(request.getOID(),
                    request.getValue());
        } else {
            final Control[] controlArray = new Control[controls.size()];
            controls.toArray(controlArray);
            extendedRequest = new ExtendedRequest(request.getOID(),
                    request.getValue(), controlArray);
        }
        extendedRequest.setIntermediateResponseListener(this);

        try {
            final ExtendedResult extendedResult =
                    ldapConnection.processExtendedOperation(extendedRequest);

            final ExtendedResponseProtocolOp extendedResponseProtocolOp =
                    new ExtendedResponseProtocolOp(
                            extendedResult.getResultCode().intValue(),
                            extendedResult.getMatchedDN(),
                            extendedResult.getDiagnosticMessage(),
                            Arrays.asList(extendedResult.getReferralURLs()),
                            extendedResult.getOID(), extendedResult.getValue());
            logger.debug(messageID + "-+-" + extendedResponseProtocolOp + "-+-" + Arrays.asList(extendedResult.getResponseControls()));
            return new LDAPMessage(messageID, extendedResponseProtocolOp,
                    Arrays.asList(extendedResult.getResponseControls()));
        } catch (final LDAPException le) {
            Debug.debugException(le);

            final ExtendedResponseProtocolOp extendedResponseProtocolOp =
                    new ExtendedResponseProtocolOp(le.getResultCode().intValue(),
                            le.getMatchedDN(), le.getMessage(),
                            Arrays.asList(le.getReferralURLs()), null, null);
            logger.debug(messageID + "-+-" + extendedResponseProtocolOp + "-+-" + Arrays.asList(le.getResponseControls()));
            return new LDAPMessage(messageID, extendedResponseProtocolOp,
                    Arrays.asList(le.getResponseControls()));
        }
    }

    @Override
    public LDAPMessage processModifyRequest(int messageID, ModifyRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final ModifyRequest modifyRequest = new ModifyRequest(request.getDN(),
                request.getModifications());
        if (!controls.isEmpty()) {
            modifyRequest.setControls(controls);
        }
        modifyRequest.setIntermediateResponseListener(this);

        LDAPResult modifyResult;
        try {
            modifyResult = ldapConnection.modify(modifyRequest);
        } catch (final LDAPException le) {
            Debug.debugException(le);
            modifyResult = le.toLDAPResult();
        }

        final ModifyResponseProtocolOp modifyResponseProtocolOp =
                new ModifyResponseProtocolOp(modifyResult.getResultCode().intValue(),
                        modifyResult.getMatchedDN(), modifyResult.getDiagnosticMessage(),
                        Arrays.asList(modifyResult.getReferralURLs()));

        logger.debug(messageID + "-+-" + modifyResponseProtocolOp + "-+-" + Arrays.asList(modifyResult.getResponseControls()));
        return new LDAPMessage(messageID, modifyResponseProtocolOp,
                Arrays.asList(modifyResult.getResponseControls()));
    }

    @Override
    public LDAPMessage processModifyDNRequest(int messageID, ModifyDNRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.debug(messageID + "-+-" + request + "-+-" + controls);
        final ModifyDNRequest modifyDNRequest = new ModifyDNRequest(request.getDN(),
                request.getNewRDN(), request.deleteOldRDN(),
                request.getNewSuperiorDN());
        if (!controls.isEmpty()) {
            modifyDNRequest.setControls(controls);
        }
        modifyDNRequest.setIntermediateResponseListener(this);

        LDAPResult modifyDNResult;
        try {
            modifyDNResult = ldapConnection.modifyDN(modifyDNRequest);
        } catch (final LDAPException le) {
            Debug.debugException(le);
            modifyDNResult = le.toLDAPResult();
        }

        final ModifyDNResponseProtocolOp modifyDNResponseProtocolOp =
                new ModifyDNResponseProtocolOp(
                        modifyDNResult.getResultCode().intValue(),
                        modifyDNResult.getMatchedDN(),
                        modifyDNResult.getDiagnosticMessage(),
                        Arrays.asList(modifyDNResult.getReferralURLs()));

        logger.debug(messageID + "-+-" + modifyDNResponseProtocolOp + "-+-" + Arrays.asList(modifyDNResult.getResponseControls()));
        return new LDAPMessage(messageID, modifyDNResponseProtocolOp,
                Arrays.asList(modifyDNResult.getResponseControls()));
    }

    @Override
    public void intermediateResponseReturned(IntermediateResponse intermediateResponse) {
        try {
            logger.debug(intermediateResponse.getMessageID() + "-+-" + intermediateResponse + "-+-" + intermediateResponse.getControls());
            listenerConnection.sendIntermediateResponse(
                    intermediateResponse.getMessageID(),
                    new IntermediateResponseProtocolOp(intermediateResponse.getOID(),
                            intermediateResponse.getValue()),
                    intermediateResponse.getControls());
        } catch (final LDAPException le) {
            Debug.debugException(le);
        }
    }
}
