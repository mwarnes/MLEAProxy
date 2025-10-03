package com.marklogic.handlers.undertow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.Utils;
import com.marklogic.beans.saml;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.signature.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.util.zip.DataFormatException;

@Controller
public class SAMLAuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(SAMLAuthHandler.class);

    @Autowired
    private saml saml;

    @GetMapping(value = "/saml/auth")
    public String authn(Model model,@RequestParam(value = "SAMLRequest") String req) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(SAMLAuthHandler.class).setLevel(saml.getCfg().samlDebug() ? Level.DEBUG : Level.INFO);
        model.addAttribute(saml);
        try {
            logger.debug("Encoded and deflated SAML Request: {}",req);
            String decodedreq = Utils.decodeMessage(req);
            logger.debug("Decoded SAML Request: {}",decodedreq);
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(new ByteArrayInputStream(decodedreq.getBytes(StandardCharsets.UTF_8)));
            Element element = document.getDocumentElement();
            DefaultBootstrap.bootstrap();
            UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
            Unmarshaller aUnMarsh = unmarshallerFactory.getUnmarshaller(element);
            AuthnRequest authnRequest = (AuthnRequest) aUnMarsh.unmarshall(element);

            logger.debug("Authn Request ConsumerServiceURL: {}",authnRequest.getAssertionConsumerServiceURL());
            logger.debug("Authn Request ID: {}",authnRequest.getID());
            saml.setAssertionUrl(authnRequest.getAssertionConsumerServiceURL());
            saml.setSamlid(authnRequest.getID());
            saml.setSamlRequest(decodedreq);
            LocalDateTime instant = LocalDateTime.now();
            String notbefore_date = instant.toString();
            saml.setNotbefore_date(notbefore_date);
            LocalDateTime instanceplus = instant.plusSeconds(saml.getCfg().SamlResponseDuration());
            String notorafter_date = instanceplus.toString();
            saml.setNotafter_date(notorafter_date);

        } catch (IOException | ParserConfigurationException | SAXException | ConfigurationException | UnmarshallingException | DataFormatException e) {
            e.printStackTrace();
        }

        return "authn";
    }

    @PostMapping(value = "/saml/auth")
    public String authz(@ModelAttribute saml saml,
                        @RequestParam(value = "userid", defaultValue = "") String userid,
                        @RequestParam(value = "roles", defaultValue = "") String roles,
                        @RequestParam(value = "authn", defaultValue = "") String authn,
                        @RequestParam(value = "notbefore_date", required = false) String notbefore_date,
                        @RequestParam(value = "notafter_date", required = false) String notafter_date,
                        @RequestParam(value = "samlid", required = false) String samlid,
                        @RequestParam(value = "assertionUrl", required = false) String assertionUrl,
                       @RequestBody String body) throws MarshallingException, ConfigurationException, CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, NoSuchProviderException, TransformerException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(SAMLAuthHandler.class).setLevel(Level.DEBUG);

        logger.debug("parms {}",body);

        saml.setUserid(userid);
        saml.setRoles(roles);
        saml.setAuthnResult(authn);
        saml.setNotbefore_date(notbefore_date);
        saml.setNotafter_date(notafter_date);
        saml.setSamlid(samlid);
        saml.setAssertionUrl(assertionUrl);
        String response = Utils.generateSAMLResponse(saml);
        logger.debug("SAMLResponse {}",new String(Utils.b64d(response)));
        saml.setSamlResponse(response);

        return "redirect";
    }
}
