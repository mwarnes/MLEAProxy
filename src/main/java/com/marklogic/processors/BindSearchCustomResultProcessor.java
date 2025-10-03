package com.marklogic.processors;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.aeonbits.owner.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.marklogic.configuration.ProcessorConfig;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.AddRequestProtocolOp;
import com.unboundid.ldap.protocol.AddResponseProtocolOp;
import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.protocol.BindResponseProtocolOp;
import com.unboundid.ldap.protocol.CompareRequestProtocolOp;
import com.unboundid.ldap.protocol.CompareResponseProtocolOp;
import com.unboundid.ldap.protocol.DeleteRequestProtocolOp;
import com.unboundid.ldap.protocol.DeleteResponseProtocolOp;
import com.unboundid.ldap.protocol.ExtendedRequestProtocolOp;
import com.unboundid.ldap.protocol.ExtendedResponseProtocolOp;
import com.unboundid.ldap.protocol.IntermediateResponseProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.ModifyDNRequestProtocolOp;
import com.unboundid.ldap.protocol.ModifyDNResponseProtocolOp;
import com.unboundid.ldap.protocol.ModifyRequestProtocolOp;
import com.unboundid.ldap.protocol.ModifyResponseProtocolOp;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.protocol.SearchResultDoneProtocolOp;
import com.unboundid.ldap.protocol.SearchResultEntryProtocolOp;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.IntermediateResponse;
import com.unboundid.ldap.sdk.IntermediateResponseListener;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.util.Debug;
import com.unboundid.util.StaticUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * Created by mwarnes on 05/02/2017.
 */
public class BindSearchCustomResultProcessor implements IRequestProcessor, IntermediateResponseListener {

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(BindSearchCustomResultProcessor.class);
    
    // Security constants
    private static final int MAX_BIND_DN_LENGTH = 512;
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final String[] SENSITIVE_ATTRIBUTES = {"password", "pwd", "userPassword", "secret", "token"};

    private ProcessorConfig cfg;
    private LDAPListenerClientConnection listenerConnection;

    @Override
    public void initialize(Config cfg) throws Exception {

        this.cfg = (ProcessorConfig) cfg;
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(BindSearchCustomResultProcessor.class).setLevel(Level.valueOf(((ProcessorConfig) cfg).debugLevel()));
        context.getLogger(SearchResultListener.class).setLevel(Level.valueOf(((ProcessorConfig) cfg).debugLevel()));

    }

    /**
     * Masks sensitive information for secure logging
     */
    private String maskSensitiveData(String input) {
        if (input == null) return "null";
        
        String masked = input;
        for (String sensitive : SENSITIVE_ATTRIBUTES) {
            masked = masked.replaceAll("(?i)" + sensitive + "[=:]\\s*[^\\s,)]+", sensitive + "=***");
        }
        
        // Truncate long strings
        if (masked.length() > 200) {
            masked = masked.substring(0, 200) + "...";
        }
        
        return masked;
    }
    
    /**
     * Validates bind request parameters for security
     */
    private boolean isValidBindRequest(BindRequestProtocolOp request) {
        if (request.getBindDN().length() > MAX_BIND_DN_LENGTH) {
            logger.warn("Bind DN exceeds maximum allowed length: {}", request.getBindDN().length());
            return false;
        }
        
        // Note: Password length validation would require safer access to credentials
        // Skipping password length check to avoid null pointer issues with LDAP SDK
        
        return true;
    }
    
    @Override
    public LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.info("BindSearchCustomResultProcessor.authenticate called.");
        
        // Validate request parameters
        if (!isValidBindRequest(request)) {
            logger.warn("Invalid bind request blocked for security reasons");
            BindResponseProtocolOp errorResponse = new BindResponseProtocolOp(
                ResultCode.INVALID_CREDENTIALS.intValue(), null, "Invalid request parameters", null, null);
            return new LDAPMessage(messageID, errorResponse, Collections.emptyList());
        }
        
        // Log request with sensitive data masked
        logger.info("Processing bind request: {}", maskSensitiveData(request.toString()));

        LDAPResult bindResult = new LDAPResult(messageID, ResultCode.SUCCESS);
        if (cfg.parm1() != null && !cfg.parm1().isEmpty()) {
            try {
                bindResult = new LDAPResult(messageID, ResultCode.valueOf(Integer.parseInt(cfg.parm1())));
            } catch (NumberFormatException e) {
                logger.error("Invalid result code parameter: {}", cfg.parm1(), e);
                bindResult = new LDAPResult(messageID, ResultCode.OTHER);
            }
        }

        // Return Bind response
        BindResponseProtocolOp bindResponseProtocolOp =
                new BindResponseProtocolOp(bindResult.getResultCode().intValue(),
                        bindResult.getMatchedDN(), bindResult.getDiagnosticMessage(),
                        Arrays.asList(bindResult.getReferralURLs()), null);

        logger.info(bindResponseProtocolOp.toString());
        return new LDAPMessage(messageID, bindResponseProtocolOp,
                Arrays.asList(bindResult.getResponseControls()));
    }

    @Override
    public LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.info("BindSearchCustomResultProcessor.search called.");
        logger.info(request.toString());

        final String[] attrs;
        final List<String> attrList = request.getAttributes();
        if (attrList.isEmpty()) {
            attrs = StaticUtils.NO_STRINGS;
        } else {
            attrs = new String[attrList.size()];
            attrList.toArray(attrs);
        }

        SearchResult searchResult = null;
        ArrayList<Entry> entries = new ArrayList<>();
        if (cfg.parm1() == null || cfg.parm1().isEmpty()) {
            try {
                ResultCode resultCode = (cfg.parm2() != null && !cfg.parm2().isEmpty()) 
                    ? ResultCode.valueOf(Integer.parseInt(cfg.parm2()))
                    : ResultCode.SUCCESS;
                searchResult = new SearchResult(new LDAPResult(messageID, resultCode));
            } catch (NumberFormatException e) {
                logger.error("Invalid result code parameter parm2: {}", cfg.parm2(), e);
                searchResult = new SearchResult(new LDAPResult(messageID, ResultCode.OTHER));
            }

        } else {
            // Build Attribute list to return
            this.listenerConnection = listenerConnection;
            SearchResultListener searchListener = new SearchResultListener(listenerConnection, messageID);
            SearchRequest searchRequest = new SearchRequest(searchListener, request.getBaseDN(), request.getScope(), request.getDerefPolicy(), request.getSizeLimit(), request.getTimeLimit(), request.typesOnly(), request.getFilter(), attrs);
            List<String> attributeList = searchRequest.getAttributeList();
            ArrayList<Attribute> retAttr = new ArrayList<>();
            Attribute objClass = new Attribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson");
            retAttr.add(objClass);

//            <GUID=7c448a67-923b-48ee-b21a-3451f2f758ca>
            //objectGUID

//            UUID uuid = UUID.fromString("9f881758-0b4a-4eaa-b59f-b6dea0934223");
//            byte[] result = getBytesFromUUID(uuid);
//            Attribute uuidObj = new Attribute("objectGUID",result);
//            retAttr.add(uuidObj);

            if ( !cfg.parm4().isEmpty()) {
                String[] keyValue = cfg.parm4().split(":");
                Attribute attr = new Attribute(keyValue[0], keyValue[1]);
                retAttr.add(attr);
            }
            if ( !cfg.parm5().isEmpty()) {
                String[] keyValue = cfg.parm5().split(":");
                Attribute attr = new Attribute(keyValue[0], keyValue[1]);
                retAttr.add(attr);
            }
            if ( !cfg.parm6().isEmpty()) {
                String[] keyValue = cfg.parm6().split(":");
                Attribute attr = new Attribute(keyValue[0], keyValue[1]);
                retAttr.add(attr);
            }
            if ( !cfg.parm7().isEmpty()) {
                String[] keyValue = cfg.parm7().split(":");
                Attribute attr = new Attribute(keyValue[0], keyValue[1]);
                retAttr.add(attr);
            }
            if ( !cfg.parm8().isEmpty()) {
                String[] keyValue = cfg.parm8().split(":");
                Attribute attr = new Attribute(keyValue[0], keyValue[1]);
                retAttr.add(attr);
            }
            if ( !cfg.parm9().isEmpty()) {
                String[] keyValue = cfg.parm9().split(":");
                Attribute attr = new Attribute(keyValue[0], keyValue[1]);
                retAttr.add(attr);
            }
            if ( !cfg.parm10().isEmpty()) {
                String[] keyValue = cfg.parm10().split(":");
                Attribute attr = new Attribute(keyValue[0], keyValue[1]);
                retAttr.add(attr);
            }

            // Build and send new Search Result Entry to client
            SearchResultEntry sre = new SearchResultEntry(messageID,cfg.parm3(),retAttr);
            entries.add(sre);
            SearchResultEntryProtocolOp searchResultEntryProtocolOp = new SearchResultEntryProtocolOp(cfg.parm3(),retAttr);
            try {
                listenerConnection.sendSearchResultEntry(messageID, searchResultEntryProtocolOp, new Control[0]);
                logger.debug(searchResultEntryProtocolOp.toString());
            } catch (LDAPException e) {
                logger.error("Error sending search result entry: {}", e.getExceptionMessage(), e);
            }

            searchResult = new SearchResult(new LDAPResult(messageID, ResultCode.SUCCESS));
        }

        // Return result
        SearchResultDoneProtocolOp searchResultDoneProtocolOp = new SearchResultDoneProtocolOp(((LDAPResult)searchResult).getResultCode().intValue(), ((LDAPResult)searchResult).getMatchedDN(), ((LDAPResult)searchResult).getDiagnosticMessage(), Arrays.asList(((LDAPResult)searchResult).getReferralURLs()));
        logger.info(searchResultDoneProtocolOp.toString());
        return new LDAPMessage(messageID, searchResultDoneProtocolOp, Arrays.asList(((LDAPResult)searchResult).getResponseControls()));
    }

    @Override
    public LDAPMessage processAddRequest(int messageID, AddRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.error("LDAP ADD request not accepted by BindSearchCustomResultProcessor.");
        logger.debug("LDAP Request: " + request);

        final AddResponseProtocolOp addResponseProtocolOp =
                new AddResponseProtocolOp(53,
                        null, null,
                        null);
        logger.debug("LDAP Response: " + addResponseProtocolOp);
        return new LDAPMessage(messageID, addResponseProtocolOp,
                controls);
    }

    @Override
    public LDAPMessage processCompareRequest(int messageID, CompareRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.error("LDAP COMPARE request not accepted by BindSearchCustomResultProcessor.");
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

    @Override
    public LDAPMessage processDeleteRequest(int messageID, DeleteRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.error("LDAP DELETE request not accepted by BindSearchCustomResultProcessor.");
        logger.debug("LDAP Request: " + request);

        final DeleteResponseProtocolOp deleteResponseProtocolOp =
                new DeleteResponseProtocolOp(53,
                        null, null,
                        null);

        logger.debug("LDAP Response: " + deleteResponseProtocolOp);
        return new LDAPMessage(messageID, deleteResponseProtocolOp,
                controls);
    }

    @Override
    public LDAPMessage processExtendedRequest(int messageID, ExtendedRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.error("LDAP EXTENDED request not accepted by BindSearchCustomResultProcessor.");
        logger.debug("LDAP Request: " + request);

        final ExtendedResponseProtocolOp extendedResponseProtocolOp =
                new ExtendedResponseProtocolOp(53,
                        null, null,
                        null, null, null);

        logger.debug("LDAP Response: " + extendedResponseProtocolOp);
        return new LDAPMessage(messageID, extendedResponseProtocolOp,
                controls);
    }

    @Override
    public LDAPMessage processModifyRequest(int messageID, ModifyRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.error("LDAP MODIFY request not accepted by BindSearchCustomResultProcessor.");
        logger.debug("LDAP Request: " + request);

        final ModifyResponseProtocolOp modifyResponseProtocolOp =
                new ModifyResponseProtocolOp(53,
                        null, null,
                        null);

        logger.debug("LDAP Response: " + modifyResponseProtocolOp);
        return new LDAPMessage(messageID, modifyResponseProtocolOp,
                controls);
    }

    @Override
    public LDAPMessage processModifyDNRequest(int messageID, ModifyDNRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.error("LDAP MODIFYDN request not accepted by BindSearchCustomResultProcessor.");
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

    @Override
    public void intermediateResponseReturned(IntermediateResponse intermediateResponse) {
        try {
            this.listenerConnection.sendIntermediateResponse(intermediateResponse.getMessageID(), new IntermediateResponseProtocolOp(intermediateResponse.getOID(), intermediateResponse.getValue()), intermediateResponse.getControls());
        } catch (LDAPException var3) {
            Debug.debugException(var3);
        }
    }

    public static byte[] getBytesFromUUID(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());

        return bb.array();
    }


}
