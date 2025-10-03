package com.marklogic.beans;

import com.marklogic.configuration.ApplicationConfig;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component("saml")
public class saml implements Serializable {

    private String userid;
    private String roles;
    private String assertionUrl;
    private String authnResult;
    private String samlid;
    private String samlRequest;
    private String samlResponse;
    private String notbefore_date;
    private String notafter_date;

    private static ApplicationConfig cfg;

    public static ApplicationConfig getCfg() {
        return cfg;
    }

    public void setCfg(ApplicationConfig cfg) {
        this.cfg = cfg;
    }


    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public String getAssertionUrl() {
        return assertionUrl;
    }

    public void setAssertionUrl(String assertionUrl) {
        this.assertionUrl = assertionUrl;
    }

    public String getAuthnResult() {
        return authnResult;
    }

    public void setAuthnResult(String authnResult) {
        this.authnResult = authnResult;
    }

    public String getSamlid() {
        return samlid;
    }

    public void setSamlid(String samlid) {
        this.samlid = samlid;
    }

    public String getSamlRequest() {
        return samlRequest;
    }

    public void setSamlRequest(String samlRequest) {
        this.samlRequest = samlRequest;
    }

    public String getNotbefore_date() {
        return notbefore_date;
    }

    public void setNotbefore_date(String notbefore_date) {
        this.notbefore_date = notbefore_date;
    }

    public String getNotafter_date() {
        return notafter_date;
    }

    public void setNotafter_date(String notafter_date) {
        this.notafter_date = notafter_date;
    }

    public String getSamlResponse() {
        return samlResponse;
    }

    public void setSamlResponse(String samlResponse) {
        this.samlResponse = samlResponse;
    }

}
