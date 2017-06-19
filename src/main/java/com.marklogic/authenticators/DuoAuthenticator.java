package com.marklogic.authenticators;

import com.duoauth.client.Http;
import com.marklogic.configuration.DuoConfig;
import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.sdk.*;
import org.aeonbits.owner.Config;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mwarnes on 02/02/2017.
 */
public class DuoAuthenticator implements IAuthenticator{

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(DuoAuthenticator.class);

    private DuoConfig appCfg;

    @Override
    public void initialize(Config cfg) throws Exception {

        this.appCfg = (DuoConfig) cfg;

        logger.debug("Duo ADMIN_DN" + appCfg.duoAdminDn());
        logger.debug("Duo BASE_DN" + appCfg.duoBaseDn());
        logger.debug("Duo UID_ATTR" + appCfg.duoUseridAttribute());
        logger.debug("Duo Host" + appCfg.duoHost());
        logger.debug("Duo IKey" + appCfg.duoIkey());
        logger.debug("Duo SKey" + appCfg.duoSkey());

    }

    @Override
    public LDAPResult authenticate(BindRequestProtocolOp request, LDAPResult result, String token) {
        logger.info("Duo Authenticator called.");
        LDAPResult bindResult = result;
        String userid=null;

        try {
            DN dn = new DN(request.getBindDN());
            if (dn.matchesBaseAndScope(appCfg.duoBaseDn(), SearchScope.SUB)) {
                logger.info("Checking userid with Duo Authenticator Servers");
                for (String r : dn.getRDNStrings()) {
                    logger.debug("RDN: " + r);
                    RDN rdn = new RDN(r);
                    if (rdn.hasAttribute(appCfg.duoUseridAttribute())) {
                        logger.debug("Found Userid Attribute ");
                        userid=rdn.getAttributeValues()[0];
                        logger.debug("Userid Attribute value " + rdn.getAttributeValues()[0]);
                    }
                }
            } else {
                logger.info("Bypassing Duo Authenticator for User DN");
                return bindResult;
            }
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
        }
        // If no userid attribute is found bail out with an Invalid Syntax error
        // In theory this shouldn't happen but ya never know.
        if (userid==null) {
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(),ResultCode.INVALID_DN_SYNTAX ,"Unable to locate userid attribute " + appCfg.duoUseridAttribute() + "in user DN.",result.getMatchedDN(),result.getReferralURLs(),result.getResponseControls());
            return failedBindResult;
        }
        if (token == null) {
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(), ResultCode.AUTHORIZATION_DENIED, "Duo Authenticator Token missing.", result.getMatchedDN(), result.getReferralURLs(), result.getResponseControls());
            return failedBindResult;
        }

        logger.info("Duo Authenticating userid: " + userid);
        logger.debug("Duo Authenticate token: " + token);

        // Check if Duo Authenticator server is available.
        // If not return BindResult based on "allow_if_down" property and return Single Factor LDAP only result.
        try {
            logger.info("Checking Duo server status.");
            Http duoRequest = new Http("GET",
                    appCfg.duoHost(),
                    "/auth/v2/ping");
            JSONObject duoResult = (JSONObject)duoRequest.executeRequest();
            logger.debug("Duo Ping Status: " + duoResult);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
            if (appCfg.allowIfDown()) {
                logger.info("Duo server not available but \"allow_if_down\" is true, returning original LDAP Bind result");
                return bindResult;
            } else {
                logger.info("Access Denied: Duo server not available and \"allow_if_down\" is false");
                bindResult = new LDAPResult(result.getMessageID(), ResultCode.SERVER_DOWN, e.getMessage(), result.getMatchedDN(), result.getReferralURLs(), result.getResponseControls());
                return bindResult;
            }
        }

        // Check if Duo credentials are still valid
        try {
            Http duoRequest = new Http("GET",
                    appCfg.duoHost(),
                    "/auth/v2/check");
            duoRequest.signRequest(appCfg.duoIkey(), appCfg.duoSkey(),2);
            JSONObject duoResult = (JSONObject)duoRequest.executeRequest();
            logger.debug("Duo Credential Check result: " + duoResult);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(),ResultCode.LOCAL_ERROR ,e.getMessage(),result.getMatchedDN(),result.getReferralURLs(),result.getResponseControls());
            return failedBindResult;
        }

        // PreAuth
        try {
            Http duoRequest = new Http("POST",
                    appCfg.duoHost(),
                    "/auth/v2/preauth");
            duoRequest.addParam("username",userid);
            duoRequest.signRequest(appCfg.duoIkey(), appCfg.duoSkey(),2);
            JSONObject duoResult = (JSONObject)duoRequest.executeRequest();
            logger.debug("Duo PreAuth: " + duoResult);
            if (duoResult.getString("result").equalsIgnoreCase("AUTH")) {
                logger.info(duoResult.getString("status_msg"));
            } else if (duoResult.getString("result").equalsIgnoreCase("ENROLL")) {
                String e = duoResult.getString("status_msg") + " at " + duoResult.getString("enroll_portal_url");
                logger.error(e);
                throw new Exception(e);
            } else if (duoResult.getString("result").equalsIgnoreCase("ALLOW")) {
                logger.info(duoResult.getString("status_msg"));
                return bindResult;
            } else if (duoResult.getString("result").equalsIgnoreCase("DENY")) {
                String e = duoResult.getString("status_msg");
                logger.error(e);
                throw new Exception(e);
            } else {
                String e = "Duo result not recognized.";
                logger.error(e);
                throw new Exception(e);
            }
        } catch (Exception e) {
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(),ResultCode.AUTHORIZATION_DENIED ,e.getMessage(),result.getMatchedDN(),result.getReferralURLs(),result.getResponseControls());
            return failedBindResult;
        }

        // Auth
        /*
         * Token could be a passcode,push,sms or phone request
         */
        try {
            // Passcode
            if (StringUtils.isNumeric(token)) {
                Http duoRequest = new Http("POST",
                        appCfg.duoHost(),
                        "/auth/v2/auth");
                duoRequest.addParam("username",userid);
                duoRequest.addParam("factor","passcode");
                duoRequest.addParam("passcode",token);
                duoRequest.signRequest(appCfg.duoIkey(), appCfg.duoSkey(),2);
                JSONObject duoResult = (JSONObject)duoRequest.executeRequest();
                logger.debug("Duo Auth: " + duoResult);
                if (duoResult.getString("result").equalsIgnoreCase("DENY")) {
                    logger.error(duoResult.getString("status_msg"));
                    throw new Exception(duoResult.getString("status_msg"));
                }
                logger.info(duoResult.getString("status_msg"));

            } else if (token.toUpperCase().startsWith("PUSH")) {
                Http duoRequest = new Http("POST",
                        appCfg.duoHost(),
                        "/auth/v2/auth");
                duoRequest.addParam("username",userid);
                duoRequest.addParam("factor","push");
                duoRequest.addParam("device","auto");
                duoRequest.signRequest(appCfg.duoIkey(), appCfg.duoSkey(),2);
                JSONObject duoResult = (JSONObject)duoRequest.executeRequest();
                logger.debug("Duo Auth: " + duoResult);
                if (duoResult.getString("result").equalsIgnoreCase("DENY")) {
                    logger.error(duoResult.getString("status_msg"));
                    throw new Exception(duoResult.getString("status_msg"));
                }
                logger.info(duoResult.getString("status_msg"));

            } else if (token.toUpperCase().startsWith("SMS")) {
                Http duoRequest = new Http("POST",
                        appCfg.duoHost(),
                        "/auth/v2/auth");
                duoRequest.addParam("username",userid);
                duoRequest.addParam("factor","sms");
                duoRequest.addParam("device","auto");
                duoRequest.signRequest(appCfg.duoIkey(), appCfg.duoSkey(),2);
                JSONObject duoResult = (JSONObject)duoRequest.executeRequest();
                logger.debug("Duo Auth: " + duoResult);
                if (duoResult.getString("result").equalsIgnoreCase("DENY")) {
                    throw new Exception(duoResult.getString("status_msg"));
                }
                logger.info(duoResult.getString("status_msg"));

            } else if (token.toUpperCase().startsWith("PHONE")) {
                String e = "Phone authentication not supported by this proxy.";
                logger.error(e);
                throw new Exception(e);

            } else {
                String e = "Requested authentication " + token + " not recognised.";
                logger.error(e);
                throw new Exception(e);
            }
        } catch (Exception e) {
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(), ResultCode.AUTHORIZATION_DENIED, e.getMessage(), result.getMatchedDN(), result.getReferralURLs(), result.getResponseControls());
            return failedBindResult;
        }



        return bindResult;
    }

}
