package com.marklogic.handlers.undertow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * OAuth Info Page Handler
 * 
 * Provides informational pages for OAuth endpoints when accessed via GET.
 */
@Controller
public class OAuthInfoHandler {

    @Autowired
    private Environment environment;

    /**
     * GET /oauth/token - Info page
     * Shows information about the OAuth token endpoint and example usage
     */
    @GetMapping(value = "/oauth/token")
    public String tokenInfo(Model model) {
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String hostname = getServerHostname();
        String baseUrl;

        // Prefer the HTTPS listener so this page matches the advertised discovery
        // metadata: MarkLogic 12.1 requires HTTPS for the token and JWKS URIs.
        String configuredBaseUrl = environment.getProperty("oauth.server.base.url");
        boolean httpsEnabled = Boolean.parseBoolean(
            environment.getProperty("mleaproxy.https.enabled", "true"));
        int httpsPort = Integer.parseInt(environment.getProperty("mleaproxy.https.port", "8443"));

        if (configuredBaseUrl != null && !configuredBaseUrl.isEmpty()) {
            baseUrl = configuredBaseUrl;
        } else if (httpsEnabled && httpsPort > 0) {
            baseUrl = "https://" + hostname + ":" + httpsPort + contextPath;
        } else {
            String port = environment.getProperty("local.server.port", "8080");
            boolean sslEnabled = Boolean.parseBoolean(
                environment.getProperty("server.ssl.enabled", "false"));
            String protocol = sslEnabled ? "https" : "http";
            baseUrl = protocol + "://" + hostname + ":" + port + contextPath;
        }
        
        model.addAttribute("tokenUrl", baseUrl + "/oauth/token");
        model.addAttribute("jwksUrl", baseUrl + "/oauth/jwks");
        model.addAttribute("configUrl", baseUrl + "/.well-known/openid-configuration");
        
        // Build example curl command
        String exampleCurl = String.format(
            "curl -s -X POST %s \\\n" +
            "  -H \"Content-Type: application/x-www-form-urlencoded\" \\\n" +
            "  -d \"grant_type=password\" \\\n" +
            "  -d \"client_id=mleaproxy\" \\\n" +
            "  -d \"client_secret=secret\" \\\n" +
            "  -d \"username=admin\" \\\n" +
            "  -d \"password=password\" | jq",
            baseUrl + "/oauth/token"
        );
        model.addAttribute("exampleCurl", exampleCurl);
        
        return "oauth-info";
    }

    /**
     * Gets the server's hostname, preferring the canonical hostname (FQDN).
     */
    private String getServerHostname() {
        try {
            String canonicalHostname = java.net.InetAddress.getLocalHost().getCanonicalHostName();
            if (canonicalHostname != null && !canonicalHostname.isEmpty() 
                && !canonicalHostname.equals("localhost") 
                && !canonicalHostname.equals("localhost.localdomain")) {
                return canonicalHostname;
            }
            
            String simpleHostname = java.net.InetAddress.getLocalHost().getHostName();
            if (simpleHostname != null && !simpleHostname.isEmpty() 
                && !simpleHostname.equals("localhost")
                && !simpleHostname.equals("localhost.localdomain")) {
                return simpleHostname;
            }
        } catch (Exception e) {
            // Fallback to env var
        }
        
        String envHostname = System.getenv("HOSTNAME");
        if (envHostname != null && !envHostname.isEmpty() 
            && !envHostname.equals("localhost")
            && !envHostname.equals("localhost.localdomain")) {
            return envHostname;
        }
        
        return "localhost";
    }
}
