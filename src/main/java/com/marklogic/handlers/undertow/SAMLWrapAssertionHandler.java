package com.marklogic.handlers.undertow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.marklogic.beans.SamlBean;


@RestController
public class SAMLWrapAssertionHandler {

    private static final Logger logger = LoggerFactory.getLogger(SAMLWrapAssertionHandler.class);

    @Autowired
    private SamlBean saml;

    @RequestMapping(
            value = "/saml/wrapassertion",
            method = RequestMethod.POST
    )
    @ResponseBody
    public String wrap(@RequestParam(value = "encodedBody", required = false, defaultValue = "true") Boolean encodedBody,
                       @RequestParam(value = "encodedResp", required = false, defaultValue = "false") Boolean encodedResp,
                       @RequestBody String body) {
        logger.warn("SAML Wrap Assertion handler temporarily disabled during OpenSAML 4.x migration");
        throw new UnsupportedOperationException(
            "SAML Wrap Assertion is temporarily disabled during OpenSAML 4.x migration. " +
            "Please use LDAP authentication or wait for SAML modernization completion.");
    }

}
