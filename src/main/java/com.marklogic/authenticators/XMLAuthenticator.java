package com.marklogic.authenticators;

import com.marklogic.configuration.CustomConfig;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.*;
import com.unboundid.ldap.sdk.*;
import org.aeonbits.owner.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;

/**
 * Created by mwarnes on 05/02/2017.
 */
public class XMLAuthenticator implements IAuthenticator {

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(XMLAuthenticator.class);

    private CustomConfig appCfg;
    private XPathFactory xpathFactory;
    private Document document;

    @Override
    public void initialize(Config cfg) throws Exception {

        this.appCfg = (CustomConfig) cfg;

        // Parm1 should contain path to XML LDAP users file
        logger.debug("Custom parm1: " + appCfg.parm1());

        // Parse XML Users file
        DocumentBuilderFactory domFactory =
                DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        document = builder.parse(appCfg.parm1());
        xpathFactory = XPathFactory.newInstance();

    }

    @Override
    public LDAPResult authenticate(BindRequestProtocolOp request, LDAPResult result, String token) {
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
                return bindResult;
            }

        } else {
            // Not a Simple Bind
            bindResult = new LDAPResult(1, ResultCode.NOT_SUPPORTED);
        }

        logger.info(bindResult.toString());
        return bindResult;
    }

    @Override
    public SearchResult search(int messageID, SearchRequest request, LDAPListenerClientConnection listenerConnection) {
        logger.info("XML Authenticator.search called.");
        logger.info(request.toString());

        logger.debug("BaseDN: " + request.getBaseDN().toString());
        logger.debug("Filter: " + request.getFilter().toString());

        // Search for User based on LDAP Filter
        SearchResult searchResult=null;
        XPath xpath = xpathFactory.newXPath();
        StringBuilder sb = new StringBuilder();
        sb.append("/ldap/users[@basedn=").append("\"").append(request.getBaseDN().toString()).append("\"").append("]/user[" + request.getFilter().getAttributeName() + "=\"" + request.getFilter().getAssertionValue()).append("\"]/@dn");

        logger.debug(sb.toString());
        try {
            XPathExpression expr = xpath.compile(sb.toString());
            String entry = (String) expr.evaluate(document, XPathConstants.STRING);

            // If User not found return
            if (entry.isEmpty()) {
                logger.error("User not found (" + request.getFilter().getAssertionValue() +") " );
                searchResult = new SearchResult(messageID,ResultCode.NO_SUCH_OBJECT,"User Not found.",null,null, null, null, 0,0,null);
                return searchResult;
            }

            // Build Attribute list to return
            logger.debug("UserDN " + entry + "," + request.getBaseDN().toString());

            // Standard Object class
            Attribute objClass = new Attribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson");
            ArrayList retAttr = new ArrayList();
            retAttr.add(objClass);

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
                        Attribute attr = new Attribute(nodeList.item(i).getNodeName(), nod.getFirstChild().getNodeValue());
                        retAttr.add(attr);
                        logger.debug(nodeList.item(i).getNodeName() + " : " + nod.getFirstChild().getNodeValue());
                    }
                }
            }

            // Build and send new Search Result Entry to client
            SearchResultEntry sre = new SearchResultEntry(request.getLastMessageID(),entry + "," + request.getBaseDN().toString(),retAttr);
            ArrayList entries = new ArrayList();
            entries.add(sre);
            logger.debug("Response Attribute  " + entries);
            SearchResultEntryProtocolOp searchResultEntryProtocolOp = new SearchResultEntryProtocolOp(request.getBaseDN().toString(),retAttr);
            logger.info(searchResultEntryProtocolOp.toString());
            listenerConnection.sendSearchResultEntry(messageID,searchResultEntryProtocolOp,null);

            // Build new Search Result to close client session
            searchResult = new SearchResult(messageID,ResultCode.SUCCESS,null,null,null, entries, null, entries.size(),-1,null);

        } catch (Exception e) {
            // Catch and print any exceptions
            e.printStackTrace();
            logger.error(e.getLocalizedMessage());
            searchResult = new SearchResult(messageID,ResultCode.OPERATIONS_ERROR,e.getLocalizedMessage(),null,null, null, null, 0,0,null);
            return searchResult;
        }

        logger.info(searchResult.toString());
        return searchResult;
    }
}
