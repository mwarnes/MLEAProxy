package com.marklogic.processors;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.aeonbits.owner.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
import com.unboundid.ldap.sdk.DN;
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
public class XMLRequestProcessor implements IRequestProcessor, IntermediateResponseListener {

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(XMLRequestProcessor.class);

    private ProcessorConfig cfg;
    private XPathFactory xpathFactory;
    private Document document;
    private LDAPListenerClientConnection listenerConnection;

    @Override
    public void initialize(Config cfg) throws Exception {

        this.cfg = (ProcessorConfig) cfg;
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(XMLRequestProcessor.class).setLevel(Level.valueOf(((ProcessorConfig) cfg).debugLevel()));

        // Parm1 should contain path to XML LDAP users file
        logger.debug("XML file path: " + ((ProcessorConfig) cfg).parm1());

        // Parse XML Users file
        DocumentBuilderFactory domFactory =
                DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        if (((ProcessorConfig) cfg).parm1().isEmpty()) {
            logger.info("user.xml path missing using default LDAP configuration instead.");
            document = builder.parse(ClassLoader.class.getResourceAsStream("/users.xml"));
        } else {
            File f = new File(((ProcessorConfig) cfg).parm1());
            if(f.exists() && !f.isDirectory()) {
                logger.info("Using custom LDAP configuration from: " + ((ProcessorConfig) cfg).parm1());
                document = builder.parse(((ProcessorConfig) cfg).parm1());
            } else {
                logger.error("Custom LDAP configuration file not found.");
                throw new Exception(((ProcessorConfig) cfg).parm1() + " missing or invalid.");
            }

        }
        xpathFactory = XPathFactory.newInstance();

    }

    @Override
    public LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.info("XML Authenticator.authenticate called.");
        logger.info(request.toString());

        // Only SIMPLE Bind supported
        LDAPResult bindResult=null;
        if (request.getCredentialsType() == BindRequestProtocolOp.CRED_TYPE_SIMPLE) {
            String binddn = request.getBindDN();
            String passwd = request.getSimplePassword().stringValue();
            String userdn = null;
            String basedn = null;

            try {
                // Extract Bind details
                DN dn = new DN(request.getBindDN());
                userdn = dn.getRDNStrings()[0];
                basedn = request.getBindDN().substring(userdn.length()+1);

                logger.debug("BindDN " + binddn);
                logger.debug("UserDN " + userdn);
                logger.debug("Passwd " + passwd);
                logger.debug("BaseDN " + basedn);

                // Look for userPassword based on Bind details
                XPath xpath = xpathFactory.newXPath();
                StringBuilder sb = new StringBuilder();
                sb.append("/ldap/users[@basedn=").append("\"").append(basedn).append("\"").append("]/user[@dn=").append("\"").append(userdn).append("\"").append("]/userPassword");
                logger.debug("xpath query " + sb.toString());
                XPathExpression expr = xpath.compile(sb.toString());
                String userPassword = (String) expr.evaluate(document, XPathConstants.STRING);
                logger.debug("LDAP userPassword " + userPassword);

                // Check password and build LDAPResult
                if (passwd.equals(userPassword)) {
                    bindResult = new LDAPResult(1, ResultCode.SUCCESS);
                } else {
                    bindResult = new LDAPResult(1, ResultCode.INVALID_CREDENTIALS);
                }

            } catch (Exception e) {
                // Catch and print any exceptions
                e.printStackTrace();
                logger.debug(e.getLocalizedMessage());
                bindResult = new LDAPResult(1, ResultCode.OPERATIONS_ERROR);
            }

        } else {
            // Not a Simple Bind
            bindResult = new LDAPResult(1, ResultCode.NOT_SUPPORTED);
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
        logger.info("XML Authenticator.search called.");
        logger.info(request.toString());

        logger.debug("BaseDN: " + request.getBaseDN().toString());
        logger.debug("Filter: " + request.getFilter().toString());

        final String[] attrs;
        final List<String> attrList = request.getAttributes();
        if (attrList.isEmpty()) {
            attrs = StaticUtils.NO_STRINGS;
        } else {
            attrs = new String[attrList.size()];
            attrList.toArray(attrs);
        }

        // Create listener to handle any intermediate responses
        this.listenerConnection = listenerConnection;
        SearchResultListener searchListener = new SearchResultListener(listenerConnection, messageID);

        //Create SearchRequest
        SearchRequest searchRequest = new SearchRequest(searchListener, request.getBaseDN(), request.getScope(), request.getDerefPolicy(), request.getSizeLimit(), request.getTimeLimit(), request.typesOnly(), request.getFilter(), attrs);
        if(!controls.isEmpty()) {
            searchRequest.setControls(controls);
        }
        searchRequest.setIntermediateResponseListener(this);

        // Search for User based on LDAP Filter
        SearchResult searchResult=null;
        List attributeList = searchRequest.getAttributeList();
        logger.debug("Attributes: " + attributeList);
        XPath xpath = xpathFactory.newXPath();
        StringBuilder sb = new StringBuilder();
        sb.append("/ldap/users[@basedn=").append("\"").append(request.getBaseDN().toString()).append("\"").append("]/user[" + request.getFilter().getAttributeName() + "=\"" + request.getFilter().getAssertionValue()).append("\"]/@dn");

        logger.debug(sb.toString());
        try {
            XPathExpression expr = xpath.compile(sb.toString());
            String entry = (String) expr.evaluate(document, XPathConstants.STRING);

            // If User not found return
            if (entry.isEmpty()) {
                logger.error("Not found ( " + request.getFilter() +" ) " );
                searchResult = new SearchResult(messageID,ResultCode.SUCCESS,null,null,null, null, null, 0,0,null);
                SearchResultDoneProtocolOp searchResultDoneProtocolOp = new SearchResultDoneProtocolOp(((LDAPResult)searchResult).getResultCode().intValue(), ((LDAPResult)searchResult).getMatchedDN(), ((LDAPResult)searchResult).getDiagnosticMessage(), Arrays.asList(((LDAPResult)searchResult).getReferralURLs()));
                logger.info(searchResultDoneProtocolOp.toString());
                return new LDAPMessage(messageID, searchResultDoneProtocolOp, Arrays.asList(((LDAPResult)searchResult).getResponseControls()));
            }

            // Build Attribute list to return
            String userdn = entry + "," + request.getBaseDN().toString();
            logger.debug("UserDN " + userdn);

            ArrayList retAttr = new ArrayList();
            if (attributeList.contains("objectClass")) {
                // Standard Object class
                Attribute objClass = new Attribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson");
                retAttr.add(objClass);
            }

            // Add Attributes for ech XML Element in user entry including any memberOf attributes required for Group roles
            sb = new StringBuilder();
            sb.append("/ldap/users[@basedn=").append("\"").append(request.getBaseDN().toString()).append("\"").append("]/user[" + request.getFilter().getAttributeName() + "=\"" + request.getFilter().getAssertionValue()).append("\"]");
            expr = xpath.compile(sb.toString());
            Node node = (Node) expr.evaluate(document, XPathConstants.NODE);
            if(null != node) {
                NodeList nodeList = node.getChildNodes();
                for (int i = 0;null!=nodeList && i < nodeList.getLength(); i++) {
                    Node nod = nodeList.item(i);
                    if(nod.getNodeType() == Node.ELEMENT_NODE) {
                        if (attributeList.isEmpty()) {
                            // If no specific attributes requested then return everything
                            Attribute attr = new Attribute(nodeList.item(i).getNodeName(), nod.getFirstChild().getNodeValue());
                            retAttr.add(attr);
                            logger.debug(nodeList.item(i).getNodeName() + " : " + nod.getFirstChild().getNodeValue());
                        } else {
                            // Else return only requested attributes
                            if (attributeList.contains(nodeList.item(i).getNodeName())) {
                                Attribute attr = new Attribute(nodeList.item(i).getNodeName(), nod.getFirstChild().getNodeValue());
                                retAttr.add(attr);
                                logger.debug(nodeList.item(i).getNodeName() + " : " + nod.getFirstChild().getNodeValue());
                            }
                        }
                    }
                }
            }

            // Build and send new Search Result Entry to client
            SearchResultEntry sre = new SearchResultEntry(messageID,userdn,retAttr);
            ArrayList entries = new ArrayList();
            entries.add(sre);
            logger.debug("Response Attribute  " + entries);
            SearchResultEntryProtocolOp searchResultEntryProtocolOp = new SearchResultEntryProtocolOp(userdn,retAttr);
            logger.info(searchResultEntryProtocolOp.toString());
            listenerConnection.sendSearchResultEntry(messageID, searchResultEntryProtocolOp, new Control[0]);

            // Build new Search Result to close client session
            searchResult = new SearchResult(messageID,ResultCode.SUCCESS,null,null,null, entries, null, entries.size(),-1,null);

        } catch (Exception e) {
            // Catch and print any exceptions
            e.printStackTrace();
            logger.error(e.getLocalizedMessage());
            searchResult = new SearchResult(messageID,ResultCode.OPERATIONS_ERROR,e.getLocalizedMessage(),null,null, null, null, 0,0,null);
//            return searchResult;
        }

        // Return result
        SearchResultDoneProtocolOp searchResultDoneProtocolOp = new SearchResultDoneProtocolOp(((LDAPResult)searchResult).getResultCode().intValue(), ((LDAPResult)searchResult).getMatchedDN(), ((LDAPResult)searchResult).getDiagnosticMessage(), Arrays.asList(((LDAPResult)searchResult).getReferralURLs()));
        logger.info(searchResultDoneProtocolOp.toString());
        return new LDAPMessage(messageID, searchResultDoneProtocolOp, Arrays.asList(((LDAPResult)searchResult).getResponseControls()));
    }

    @Override
    public LDAPMessage processAddRequest(int messageID, AddRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection) {
        logger.error("LDAP ADD request not accepted by XMLRequestProcessor.");
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
        logger.error("LDAP COMPARE request not accepted by XMLRequestProcessor.");
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
        logger.error("LDAP DELETE request not accepted by XMLRequestProcessor.");
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
        logger.error("LDAP EXTENDED request not accepted by XMLRequestProcessor.");
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
        logger.error("LDAP MODIFY request not accepted by XMLRequestProcessor.");
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
        logger.error("LDAP MODIFYDN request not accepted by XMLRequestProcessor.");
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
}
