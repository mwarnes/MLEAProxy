package com.marklogic.handlers.undertow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.Utils;
import com.marklogic.beans.saml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.util.Objects;

@RestController
public class SAMLCaCertsHandler {

    private static final Logger logger = LoggerFactory.getLogger(SAMLCaCertsHandler.class);

    @Autowired
    private saml saml;

    @RequestMapping(
            value = "/saml/ca",
            method = RequestMethod.GET,
            produces = "text/plain"
    )
    @ResponseBody
    public String getCACerts() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(SAMLCaCertsHandler.class).setLevel(saml.getCfg().samlDebug() ? Level.DEBUG : Level.INFO);
        String content = null;
        try {
            content = Utils.getCaCertificate();
        } catch (IOException | CertificateException e) {
            e.printStackTrace();
        }
        return content;
    }

}
