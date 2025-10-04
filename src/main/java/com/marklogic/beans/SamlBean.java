package com.marklogic.beans;

import java.io.Serializable;

import org.springframework.stereotype.Component;

import com.marklogic.configuration.ApplicationConfig;
import com.marklogic.configuration.SAMLListenersConfig;

/**
 * Spring bean for storing SAML authentication request and response data.
 * <p>
 * This bean is used to pass SAML-related information between controllers and processors
 * during the SAML authentication flow. It holds user identity information, roles,
 * SAML assertion data, and configuration references.
 * </p>
 * <p>
 * The bean is request-scoped in Spring and is populated during SAML authentication
 * operations. It supports both SAML assertion generation and consumption workflows.
 * </p>
 *
 * @author MLEAProxy
 * @version 1.0
 * @since 1.0
 */
@Component("saml")
public class SamlBean implements Serializable {

    private String userid;
    private String roles;
    private String assertionUrl;
    private String authnResult;
    private String samlid;
    private String samlRequest;
    private String samlResponse;
    private String notbefore_date;
    private String notafter_date;

    private ApplicationConfig cfg;
    private SAMLListenersConfig listenerCfg;

    /**
     * Gets the application configuration.
     *
     * @return the application configuration object
     */
    public ApplicationConfig getCfg() {
        return cfg;
    }

    /**
     * Sets the application configuration.
     *
     * @param cfg the application configuration to set
     */
    public void setCfg(ApplicationConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * Gets the SAML listener configuration.
     *
     * @return the SAML listener configuration object
     */
    public SAMLListenersConfig getListenerCfg() {
        return listenerCfg;
    }

    /**
     * Sets the SAML listener configuration.
     *
     * @param listenerCfg the SAML listener configuration to set
     */
    public void setListenerCfg(SAMLListenersConfig listenerCfg) {
        this.listenerCfg = listenerCfg;
    }

    /**
     * Gets the authenticated user ID.
     *
     * @return the user ID
     */
    public String getUserid() {
        return userid;
    }

    /**
     * Sets the authenticated user ID.
     *
     * @param userid the user ID to set
     */
    public void setUserid(String userid) {
        this.userid = userid;
    }

    /**
     * Gets the user's roles as a comma-separated string.
     *
     * @return the roles string (e.g., "admin,user,developer")
     */
    public String getRoles() {
        return roles;
    }

    /**
     * Sets the user's roles as a comma-separated string.
     *
     * @param roles the roles string to set (e.g., "admin,user,developer")
     */
    public void setRoles(String roles) {
        this.roles = roles;
    }

    /**
     * Gets the SAML assertion consumer URL.
     *
     * @return the assertion URL where the SAML response should be sent
     */
    public String getAssertionUrl() {
        return assertionUrl;
    }

    /**
     * Sets the SAML assertion consumer URL.
     *
     * @param assertionUrl the assertion URL to set
     */
    public void setAssertionUrl(String assertionUrl) {
        this.assertionUrl = assertionUrl;
    }

    /**
     * Gets the authentication result status.
     *
     * @return the authentication result (e.g., "success", "failure")
     */
    public String getAuthnResult() {
        return authnResult;
    }

    /**
     * Sets the authentication result status.
     *
     * @param authnResult the authentication result to set
     */
    public void setAuthnResult(String authnResult) {
        this.authnResult = authnResult;
    }

    /**
     * Gets the SAML assertion ID.
     *
     * @return the unique SAML assertion identifier
     */
    public String getSamlid() {
        return samlid;
    }

    /**
     * Sets the SAML assertion ID.
     *
     * @param samlid the SAML assertion ID to set
     */
    public void setSamlid(String samlid) {
        this.samlid = samlid;
    }

    /**
     * Gets the SAML authentication request.
     *
     * @return the Base64-encoded SAML request
     */
    public String getSamlRequest() {
        return samlRequest;
    }

    /**
     * Sets the SAML authentication request.
     *
     * @param samlRequest the Base64-encoded SAML request to set
     */
    public void setSamlRequest(String samlRequest) {
        this.samlRequest = samlRequest;
    }

    /**
     * Gets the 'NotBefore' validity timestamp for the SAML assertion.
     *
     * @return the NotBefore date string
     */
    public String getNotbefore_date() {
        return notbefore_date;
    }

    /**
     * Sets the 'NotBefore' validity timestamp for the SAML assertion.
     *
     * @param notbefore_date the NotBefore date string to set
     */
    public void setNotbefore_date(String notbefore_date) {
        this.notbefore_date = notbefore_date;
    }

    /**
     * Gets the 'NotOnOrAfter' validity timestamp for the SAML assertion.
     *
     * @return the NotOnOrAfter date string
     */
    public String getNotafter_date() {
        return notafter_date;
    }

    /**
     * Sets the 'NotOnOrAfter' validity timestamp for the SAML assertion.
     *
     * @param notafter_date the NotOnOrAfter date string to set
     */
    public void setNotafter_date(String notafter_date) {
        this.notafter_date = notafter_date;
    }

    /**
     * Gets the SAML authentication response.
     *
     * @return the Base64-encoded SAML response
     */
    public String getSamlResponse() {
        return samlResponse;
    }

    /**
     * Sets the SAML authentication response.
     *
     * @param samlResponse the Base64-encoded SAML response to set
     */
    public void setSamlResponse(String samlResponse) {
        this.samlResponse = samlResponse;
    }

}
