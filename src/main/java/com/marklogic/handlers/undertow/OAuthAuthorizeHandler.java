package com.marklogic.handlers.undertow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.repository.JsonUserRepository;
import com.marklogic.repository.JsonUserRepository.UserInfo;
import com.marklogic.service.AuthorizationCodeStore;
import com.marklogic.service.AuthorizationCodeStore.AuthorizationCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OAuth 2.0 Authorization Endpoint.
 *
 * <p>Implements the browser-facing half of the Authorization Code flow that MarkLogic 12.1
 * uses to log AdminUI and Query Console users in. A GET renders a login page; submitting it
 * issues an authorization code and returns it to MarkLogic, which then exchanges the code
 * at {@link OAuthTokenHandler}.
 *
 * <p>Following the approach already taken by the SAML handler, the login page does not
 * verify credentials. MLEAProxy is a test and teaching tool: the operator declares which
 * username and roles they want represented, which is what makes it useful for exercising
 * MarkLogic's authorization behaviour without maintaining real accounts.
 *
 * <p>Behaviour observed from MarkLogic 12.1 and handled here:
 * <ul>
 *   <li>{@code response_mode=form_post} - the response is delivered as a self-submitting
 *       HTML form POST rather than a query-string redirect</li>
 *   <li>PKCE with {@code code_challenge_method=S256} - always sent, verified at the token
 *       endpoint</li>
 *   <li>{@code grant_type=authorization_code} sent on the <em>authorize</em> request, which
 *       is not where RFC 6749 puts it; it is accepted and ignored</li>
 *   <li>an empty {@code scope}, so roles come from this page or {@code users.json}</li>
 * </ul>
 *
 * <p>Every request is also logged in full, as both a readable block and a single JSON line
 * prefixed {@code OAUTH_AUTHORIZE_CAPTURE}, which is how the MarkLogic behaviour above was
 * established in the first place. That logging is deliberately retained.
 *
 * @since 2.0.4
 */
@Controller
public class OAuthAuthorizeHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuthAuthorizeHandler.class);

    /** Prefix that makes captured requests easy to grep out of a log file. */
    private static final String CAPTURE_MARKER = "OAUTH_AUTHORIZE_CAPTURE";

    private static final String RESPONSE_MODE_FORM_POST = "form_post";

    /** Null byte, stripped from submitted values. */
    private static final String NULL_BYTE = String.valueOf((char) 0);

    /** Request parameters defined by OAuth 2.0 (RFC 6749), PKCE (RFC 7636) and OIDC Core. */
    private static final Set<String> KNOWN_PARAMETERS = new LinkedHashSet<>(List.of(
            "response_type", "client_id", "redirect_uri", "scope", "state",
            "code_challenge", "code_challenge_method",
            "nonce", "response_mode", "prompt", "display", "max_age",
            "login_hint", "id_token_hint", "acr_values", "ui_locales", "claims",
            "request", "request_uri", "resource", "audience"));

    /** Headers whose values are replaced with a description rather than logged verbatim. */
    private static final Set<String> SENSITIVE_HEADERS = new LinkedHashSet<>(List.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie"));

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthorizationCodeStore codeStore;

    @Autowired(required = false)
    private JsonUserRepository jsonUserRepository;

    @Value("${oauth.default.roles:user}")
    private String defaultRoles;

    @Value("${oauth.token.expiration.seconds:3600}")
    private int defaultTokenLifetime;

    /**
     * Renders the login page for an authorization request.
     *
     * <p>When the request is not a usable authorization request - no {@code response_type}
     * or no {@code client_id} - the diagnostic capture page is shown instead. That keeps
     * the endpoint useful for profiling an unfamiliar client, which is how it started life.
     */
    @GetMapping("/oauth/authorize")
    public String authorize(@RequestParam MultiValueMap<String, String> parameters,
                            HttpServletRequest request,
                            Model model) {

        Map<String, Object> capture = buildCapture(parameters, request);
        logCapture(capture);

        String responseType = parameters.getFirst("response_type");
        String clientId = parameters.getFirst("client_id");

        if (responseType == null || responseType.isBlank() || clientId == null || clientId.isBlank()) {
            logger.info("Authorization request is incomplete - rendering the capture page instead "
                    + "of the login page (response_type={}, client_id={})", responseType, clientId);
            addCaptureAttributes(model, capture);
            return "oauth-authorize-capture";
        }

        model.addAttribute("clientId", clientId);
        model.addAttribute("responseType", responseType);
        model.addAttribute("redirectUri", parameters.getFirst("redirect_uri"));
        model.addAttribute("state", parameters.getFirst("state"));
        model.addAttribute("scope", parameters.getFirst("scope"));
        model.addAttribute("responseMode", parameters.getFirst("response_mode"));
        model.addAttribute("codeChallenge", parameters.getFirst("code_challenge"));
        model.addAttribute("codeChallengeMethod", parameters.getFirst("code_challenge_method"));
        model.addAttribute("pkcePresent", parameters.containsKey("code_challenge"));
        model.addAttribute("defaultRoles", defaultRoles);
        model.addAttribute("tokenLifetime", defaultTokenLifetime);
        model.addAttribute("codeTtlSeconds", codeStore.getTtlSeconds());

        return "oauth-authorize";
    }

    /**
     * Issues an authorization code, or an error, and returns it to the client.
     *
     * <p>Delivery honours {@code response_mode}: {@code form_post} renders a self-submitting
     * form, anything else appends query parameters to the redirect URI.
     */
    @PostMapping("/oauth/authorize")
    public String approve(@RequestParam(value = "username", defaultValue = "") String username,
                          @RequestParam(value = "roles", defaultValue = "") String roles,
                          @RequestParam(value = "outcome", defaultValue = "approve") String outcome,
                          @RequestParam(value = "token_lifetime", required = false) Integer tokenLifetime,
                          @RequestParam(value = "client_id", defaultValue = "") String clientId,
                          @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                          @RequestParam(value = "state", required = false) String state,
                          @RequestParam(value = "scope", required = false) String scope,
                          @RequestParam(value = "response_mode", required = false) String responseMode,
                          @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                          @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                          Model model) {

        username = sanitize(username);
        roles = sanitize(roles);
        redirectUri = sanitize(redirectUri);
        state = sanitize(state);

        if (redirectUri == null || redirectUri.isBlank()) {
            logger.error("Cannot return an authorization response: no redirect_uri was supplied "
                    + "by the client for client_id '{}'", clientId);
            model.addAttribute("error", "invalid_request");
            model.addAttribute("errorDescription",
                    "No redirect_uri was supplied by the client, so the authorization code "
                            + "cannot be returned. Set oauth-redirect-uri in the MarkLogic "
                            + "external security configuration.");
            return "oauth-authorize-error";
        }

        if (!"approve".equalsIgnoreCase(outcome)) {
            logger.info("Operator denied the authorization request for client '{}'", clientId);
            return deliver(model, redirectUri, responseMode,
                    Map.of("error", "access_denied",
                            "error_description", "The operator denied the request"),
                    state);
        }

        List<String> resolvedRoles = resolveRoles(username, roles);
        int lifetime = tokenLifetime != null ? tokenLifetime : defaultTokenLifetime;

        String code = codeStore.issue(new AuthorizationCode(
                clientId,
                redirectUri,
                scope,
                codeChallenge,
                codeChallengeMethod,
                username,
                resolvedRoles,
                lifetime,
                Instant.now()));

        logger.info("Authorization approved for user '{}' with roles {} (client '{}', "
                        + "response_mode={})",
                username, resolvedRoles, clientId,
                responseMode == null ? "query" : responseMode);

        return deliver(model, redirectUri, responseMode, Map.of("code", code), state);
    }

    /**
     * Prepares the response delivery for the requested response mode.
     *
     * <p>{@code form_post} exists because MarkLogic asks for it. The auto-submitting form
     * mirrors the approach the SAML handler already uses to return an assertion.
     */
    private String deliver(Model model, String redirectUri, String responseMode,
                           Map<String, String> params, String state) {

        Map<String, String> allParams = new LinkedHashMap<>(params);
        // state must be returned verbatim so the client can match it to its request.
        if (state != null && !state.isEmpty()) {
            allParams.put("state", state);
        }

        if (RESPONSE_MODE_FORM_POST.equalsIgnoreCase(responseMode)) {
            model.addAttribute("formAction", redirectUri);
            model.addAttribute("formFields", allParams);
            return "oauth-authorize-post";
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri);
        allParams.forEach(builder::queryParam);
        return "redirect:" + builder.build().toUriString();
    }

    /**
     * Determines the roles to embed in the eventual access token.
     *
     * <p>Mirrors the precedence used by the token and SAML handlers:
     * explicit input, then {@code users.json}, then the configured default.
     */
    private List<String> resolveRoles(String username, String roles) {
        if (roles != null && !roles.isBlank()) {
            return parseRoles(roles);
        }

        if (jsonUserRepository != null && jsonUserRepository.isInitialized()
                && username != null && !username.isBlank()) {
            UserInfo userInfo = jsonUserRepository.findByUsername(username);
            if (userInfo != null && !userInfo.getRoles().isEmpty()) {
                logger.info("Using roles from users.json for '{}': {}", username, userInfo.getRoles());
                return userInfo.getRoles();
            }
        }

        logger.info("Using default roles for '{}': {}", username, defaultRoles);
        return parseRoles(defaultRoles);
    }

    private List<String> parseRoles(String rolesParam) {
        List<String> parsed = new ArrayList<>();
        if (rolesParam != null) {
            for (String role : rolesParam.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    parsed.add(trimmed);
                }
            }
        }
        return parsed;
    }

    /** Strips null bytes, matching the defensive handling in the SAML handler. */
    private String sanitize(String value) {
        return value == null ? null : value.replace(NULL_BYTE, "");
    }

    // ---------------------------------------------------------------------------------
    // Request capture. Retained from the diagnostic stage: it is how MarkLogic's actual
    // request shape was determined, and remains the fastest way to profile a new client.
    // ---------------------------------------------------------------------------------

    private Map<String, Object> buildCapture(MultiValueMap<String, String> parameters,
                                             HttpServletRequest request) {
        Map<String, Object> capture = new LinkedHashMap<>();
        capture.put("method", request.getMethod());
        capture.put("requestUri", request.getRequestURI());
        capture.put("queryString", request.getQueryString());
        capture.put("protocol", request.getProtocol());
        capture.put("scheme", request.getScheme());
        capture.put("secure", request.isSecure());
        capture.put("localPort", request.getLocalPort());
        capture.put("remoteAddr", request.getRemoteAddr());

        Map<String, List<String>> knownParams = new LinkedHashMap<>();
        Map<String, List<String>> unknownParams = new LinkedHashMap<>();
        parameters.forEach((name, values) -> {
            if (KNOWN_PARAMETERS.contains(name.toLowerCase())) {
                knownParams.put(name, values);
            } else {
                unknownParams.put(name, values);
            }
        });
        capture.put("oauthParameters", knownParams);
        capture.put("unrecognisedParameters", unknownParams);
        capture.put("headers", collectHeaders(request));
        capture.put("pkcePresent", parameters.containsKey("code_challenge"));
        capture.put("codeChallengeMethod", parameters.getFirst("code_challenge_method"));
        return capture;
    }

    @SuppressWarnings("unchecked")
    private void addCaptureAttributes(Model model, Map<String, Object> capture) {
        model.addAttribute("capture", capture);
        model.addAttribute("knownParams", capture.get("oauthParameters"));
        model.addAttribute("unknownParams", capture.get("unrecognisedParameters"));
        model.addAttribute("headers", capture.get("headers"));
        model.addAttribute("pkcePresent", capture.get("pkcePresent"));

        Map<String, List<String>> known = (Map<String, List<String>>) capture.get("oauthParameters");
        model.addAttribute("redirectUri", first(known, "redirect_uri"));
        model.addAttribute("state", first(known, "state"));
        model.addAttribute("responseType", first(known, "response_type"));
        model.addAttribute("clientId", first(known, "client_id"));
        model.addAttribute("scope", first(known, "scope"));
    }

    private String first(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Collects request headers, masking the values of credential-bearing headers.
     *
     * <p>For profiling purposes the useful information in an {@code Authorization} header
     * is the scheme and the length, not the secret, so only those are recorded.
     */
    private Map<String, List<String>> collectHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> values = new ArrayList<>();
            Enumeration<String> headerValues = request.getHeaders(name);
            while (headerValues != null && headerValues.hasMoreElements()) {
                String value = headerValues.nextElement();
                values.add(SENSITIVE_HEADERS.contains(name.toLowerCase())
                        ? describeSensitiveValue(value)
                        : value);
            }
            headers.put(name, values);
        }
        return headers;
    }

    private String describeSensitiveValue(String value) {
        if (value == null || value.isEmpty()) {
            return "<empty>";
        }
        int space = value.indexOf(' ');
        String scheme = space > 0 ? value.substring(0, space) : "<no scheme>";
        return "<masked: scheme=" + scheme + ", length=" + value.length() + ">";
    }

    @SuppressWarnings("unchecked")
    private void logCapture(Map<String, Object> capture) {
        Map<String, List<String>> knownParams =
                (Map<String, List<String>>) capture.get("oauthParameters");
        Map<String, List<String>> unknownParams =
                (Map<String, List<String>>) capture.get("unrecognisedParameters");
        Map<String, List<String>> headers =
                (Map<String, List<String>>) capture.get("headers");

        logger.info("=== OAuth authorize request captured ===");
        logger.info("  {} {}{}", capture.get("method"), capture.get("requestUri"),
                capture.get("queryString") == null ? "" : "?" + capture.get("queryString"));
        logger.info("  scheme={} secure={} localPort={} remoteAddr={}",
                capture.get("scheme"), capture.get("secure"),
                capture.get("localPort"), capture.get("remoteAddr"));

        logger.info("  OAuth/OIDC parameters ({}):", knownParams.size());
        knownParams.forEach((name, values) -> logger.info("    {} = {}", name, join(values)));

        if (unknownParams.isEmpty()) {
            logger.info("  Unrecognised parameters: none");
        } else {
            logger.info("  Unrecognised parameters ({}):", unknownParams.size());
            unknownParams.forEach((name, values) -> logger.info("    {} = {}", name, join(values)));
        }

        logger.info("  PKCE: {}", Boolean.TRUE.equals(capture.get("pkcePresent"))
                ? "present (code_challenge_method=" + capture.get("codeChallengeMethod") + ")"
                : "absent - no code_challenge parameter");

        logger.info("  Request headers ({}):", headers.size());
        headers.forEach((name, values) -> logger.info("    {}: {}", name, join(values)));
        logger.info("========================================");

        try {
            logger.info("{} {}", CAPTURE_MARKER, objectMapper.writeValueAsString(capture));
        } catch (Exception e) {
            logger.warn("Could not serialise the captured request as JSON", e);
        }
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.size() == 1 ? values.get(0) : String.join(", ", values);
    }

    /** Exposes the recognised parameter names for tests and documentation. */
    public static Set<String> knownParameters() {
        return Collections.unmodifiableSet(KNOWN_PARAMETERS);
    }
}
