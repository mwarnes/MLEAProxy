package com.marklogic.configuration;

import com.marklogic.service.TlsCertificateService;
import io.undertow.Undertow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Adds an HTTPS listener to the embedded Undertow server.
 *
 * <p>The OAuth 2.0 Authorization Code flow redirects a browser to MLEAProxy's login page,
 * which has to be served over HTTPS. Rather than converting the whole server to TLS -
 * which would break every existing HTTP example, {@code http_client/} file and
 * resource-server walkthrough - this adds HTTPS as a <em>second</em> listener. The
 * primary {@code server.port} listener stays plain HTTP, so both are available:
 *
 * <pre>
 *   http://host:8080/oauth/token      (unchanged, Resource Server flow)
 *   https://host:8443/oauth/authorize (new, Authorization Code flow)
 * </pre>
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code mleaproxy.https.enabled} - master switch (default true)</li>
 *   <li>{@code mleaproxy.https.port} - HTTPS port, 0 or negative disables (default 8443)</li>
 *   <li>{@code mleaproxy.https.address} - bind address (default 0.0.0.0)</li>
 *   <li>{@code mleaproxy.https.certificate} - PEM server certificate path</li>
 *   <li>{@code mleaproxy.https.private-key} - PKCS#8 PEM server private key path</li>
 *   <li>{@code mleaproxy.https.ca-certificate} - PEM CA certificate path</li>
 *   <li>{@code mleaproxy.https.ca-private-key} - PKCS#8 PEM CA private key path</li>
 *   <li>{@code mleaproxy.https.subject-alt-names} - extra SANs for a generated certificate</li>
 * </ul>
 *
 * <p>Missing TLS material is generated on startup by {@link TlsCertificateService}, which
 * produces a private CA plus a server certificate signed by it. The CA certificate is the
 * file to import into MarkLogic.
 * Failure to configure TLS is logged as an error but does not stop the application: the
 * HTTP listener and every non-OAuth protocol remain usable.
 */
@Configuration
public class HttpsListenerConfig {

    private static final Logger logger = LoggerFactory.getLogger(HttpsListenerConfig.class);

    private final TlsCertificateService tlsCertificateService;

    @Value("${mleaproxy.https.enabled:true}")
    private boolean httpsEnabled;

    @Value("${mleaproxy.https.port:8443}")
    private int httpsPort;

    @Value("${mleaproxy.https.address:0.0.0.0}")
    private String httpsAddress;

    @Value("${mleaproxy.https.certificate:./certificates/tls-certificate.pem}")
    private String certificatePath;

    @Value("${mleaproxy.https.private-key:./certificates/tls-privkey.pem}")
    private String privateKeyPath;

    @Value("${mleaproxy.https.ca-certificate:./certificates/ca-certificate.pem}")
    private String caCertificatePath;

    @Value("${mleaproxy.https.ca-private-key:./certificates/ca-privkey.pem}")
    private String caPrivateKeyPath;

    @Value("${mleaproxy.https.subject-alt-names:}")
    private String subjectAltNames;

    public HttpsListenerConfig(TlsCertificateService tlsCertificateService) {
        this.tlsCertificateService = tlsCertificateService;
    }

    @Bean
    public WebServerFactoryCustomizer<UndertowServletWebServerFactory> httpsListenerCustomizer() {
        return factory -> {
            if (!httpsEnabled) {
                logger.info("HTTPS listener is disabled (mleaproxy.https.enabled=false)");
                return;
            }
            if (httpsPort <= 0) {
                logger.info("HTTPS listener is disabled (mleaproxy.https.port={})", httpsPort);
                return;
            }

            try {
                Path certPath = Paths.get(certificatePath);
                Path keyPath = Paths.get(privateKeyPath);
                Path caCertPath = Paths.get(caCertificatePath);
                Path caKeyPath = Paths.get(caPrivateKeyPath);

                tlsCertificateService.ensureCertificate(
                        caCertPath, caKeyPath, certPath, keyPath, extraSubjectAltNames());
                SSLContext sslContext =
                        tlsCertificateService.createSslContext(certPath, keyPath, caCertPath);

                factory.addBuilderCustomizers(
                        (Undertow.Builder builder) ->
                                builder.addHttpsListener(httpsPort, httpsAddress, sslContext));

                logger.info("HTTPS listener configured on {}:{}", httpsAddress, httpsPort);
            } catch (Exception e) {
                // Deliberately non-fatal: HTTP and the LDAP/SAML/Kerberos protocols still work.
                logger.error("Failed to configure the HTTPS listener on port {} - continuing with "
                        + "HTTP only. The OAuth Authorization Code flow will be unavailable.",
                        httpsPort, e);
            }
        };
    }

    private List<String> extraSubjectAltNames() {
        if (subjectAltNames == null || subjectAltNames.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(subjectAltNames.split(","));
    }
}
