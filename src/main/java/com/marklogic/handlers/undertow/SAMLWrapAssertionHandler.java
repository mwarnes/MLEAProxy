package com.marklogic.handlers.undertow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.Utils;
import com.marklogic.beans.saml;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Status;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.core.impl.ResponseBuilder;
import org.opensaml.saml2.core.impl.ResponseMarshaller;
import org.opensaml.saml2.core.impl.StatusBuilder;
import org.opensaml.saml2.core.impl.StatusCodeBuilder;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.Unmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.io.UnmarshallingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;


@RestController
public class SAMLWrapAssertionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SAMLWrapAssertionHandler.class);

    @Autowired
    private saml saml;

    @RequestMapping(
            value = "/saml/wrapassertion",
            method = RequestMethod.POST
    )
    @ResponseBody
    public String wrap(@RequestParam(value = "encodedBody", required = false, defaultValue = "true") Boolean encodedBody,
                       @RequestParam(value = "encodedResp", required = false, defaultValue = "false") Boolean encodedResp,
                       @RequestBody String body) {

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(SAMLWrapAssertionHandler.class).setLevel(saml.getCfg().samlDebug() ? Level.DEBUG : Level.INFO);
        logger.debug("encodedBody {}",encodedBody);
        logger.debug("encodedRespy {}",encodedResp);
        logger.debug("body {}", body);

        String samlResponse = null;
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(new ByteArrayInputStream(encodedBody ? Utils.b64d(body) : body.getBytes(StandardCharsets.UTF_8)));
            Element element = document.getDocumentElement();
            DefaultBootstrap.bootstrap();
            UnmarshallerFactory unmarshallerFactory = Configuration.getUnmarshallerFactory();
            Unmarshaller aUnMarsh = unmarshallerFactory.getUnmarshaller(element);
            Assertion assertion = (Assertion) aUnMarsh.unmarshall(element);
            org.opensaml.saml2.core.Response response = new ResponseBuilder().buildObject();
            response.setVersion(SAMLVersion.VERSION_20);
            response.setIssueInstant(assertion.getIssueInstant());
            Status stat = new StatusBuilder().buildObject();
            StatusCode statCode = new StatusCodeBuilder().buildObject();
            statCode.setValue("urn:oasis:names:tc:SAML:2.0:status:Success");
            stat.setStatusCode(statCode);
            response.setStatus(stat);
            response.setID(assertion.getID() + "-1");
            response.getAssertions().add(assertion);
            ResponseMarshaller rMarsh = new ResponseMarshaller();
            Element plain = rMarsh.marshall(response);
            TransformerFactory transFactory = TransformerFactory.newInstance();
            Transformer transformer = transFactory.newTransformer();
            StringWriter buffer = new StringWriter();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(plain), new StreamResult(buffer));
            samlResponse = buffer.toString();
        } catch (IOException | TransformerException | MarshallingException | ConfigurationException | UnmarshallingException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
        logger.debug("SAML Response: {}", samlResponse);

        return encodedResp ? Utils.e(samlResponse) : samlResponse;

    }

}
