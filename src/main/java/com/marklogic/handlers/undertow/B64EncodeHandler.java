package com.marklogic.handlers.undertow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.marklogic.Utils;
import com.marklogic.beans.SamlBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

@RestController
public class B64EncodeHandler {

    private static final Logger logger = LoggerFactory.getLogger(B64EncodeHandler.class);

    @Autowired
    private SamlBean saml;

    @RequestMapping(
            value = "/saml/encode",
            method = RequestMethod.POST,
            produces = "text/plain"
    )
    @ResponseBody
    public String encode(@RequestBody String body) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(B64EncodeHandler.class).setLevel(saml.getCfg().samlDebug() ? Level.DEBUG : Level.INFO);

        return Utils.e(body);
    }

    @GetMapping(
            value = "/encode",
            produces = "text/plain"
    )
    @ResponseBody
    public String encodeGet(@RequestParam("data") String data) {
        return Utils.e(data);
    }

}
