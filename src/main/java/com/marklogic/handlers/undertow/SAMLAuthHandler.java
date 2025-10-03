package com.marklogic.handlers.undertow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.Utils;
import com.marklogic.beans.saml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class B64DecodeHandler {

    private static final Logger logger = LoggerFactory.getLogger(B64DecodeHandler.class);

    @Autowired
    private saml saml;

    @RequestMapping(
            value = "/saml/decode",
            method = RequestMethod.POST,
            produces = "application/xml"
    )
    @ResponseBody
    public String encode(@RequestBody String body) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(B64DecodeHandler.class).setLevel(Level.valueOf(saml.getListenerCfg().debugLevel()));

        return new String(Utils.b64d(body));
    }

}
