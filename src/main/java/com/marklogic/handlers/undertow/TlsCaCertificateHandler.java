package com.marklogic.handlers.undertow;

import com.marklogic.service.TlsCertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves the CA certificate used by the HTTPS listener.
 *
 * <p>MarkLogic must trust this CA before it can fetch JWKS or exchange an authorization
 * code over HTTPS, and it reports the absence only as
 * {@code SVC-SOCCONN: Certificate verify failed}, which does not say which certificate is
 * needed or where to find it. Publishing the CA over HTTP means an administrator can
 * retrieve it without shell access to the MLEAProxy host.
 *
 * <p>Only the CA certificate is exposed. The server certificate is already visible to any
 * TLS client during the handshake, and neither private key is ever served.
 *
 * <p>Follows the convention of {@code /saml/ca}, which serves the SAML signing CA.
 *
 * @since 2.0.4
 */
@Controller
public class TlsCaCertificateHandler {

    private static final Logger logger = LoggerFactory.getLogger(TlsCaCertificateHandler.class);

    private final TlsCertificateService tlsCertificateService;

    @Value("${mleaproxy.https.ca-certificate:./certificates/ca-certificate.pem}")
    private String caCertificatePath;

    public TlsCaCertificateHandler(TlsCertificateService tlsCertificateService) {
        this.tlsCertificateService = tlsCertificateService;
    }

    /**
     * Returns the CA certificate as PEM text.
     *
     * <p>Served as a download so that a browser saves it rather than rendering it, which
     * is what an administrator heading for MarkLogic's Certificate Authorities import
     * actually wants.
     *
     * @return 200 with the PEM, or 404 when the HTTPS listener has generated nothing
     */
    @GetMapping(value = "/tls/ca", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public ResponseEntity<String> getCaCertificate() {
        Path path = Paths.get(caCertificatePath);
        var info = tlsCertificateService.describeCaCertificate(path);

        if (info.isEmpty()) {
            logger.warn("CA certificate requested but none is available at {}", path);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No CA certificate is available. The HTTPS listener is either "
                            + "disabled or configured with an externally supplied certificate.\n");
        }

        logger.info("Serving CA certificate ({})", info.get().subject());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mleaproxy-ca-certificate.pem\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(info.get().pem());
    }
}
