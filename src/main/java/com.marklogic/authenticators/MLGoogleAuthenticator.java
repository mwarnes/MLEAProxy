package com.marklogic.authenticators;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.configuration.MLGoogleConfig;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.*;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.aeonbits.owner.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mwarnes on 05/02/2017.
 */
public class MLGoogleAuthenticator implements IAuthenticator {

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(MLGoogleAuthenticator.class);

    private MLGoogleConfig appCfg;

    @Override
    public void initialize(Config cfg) throws Exception {

        this.appCfg = (MLGoogleConfig) cfg;

        logger.info("MarkLogic host: " + appCfg.mlHost());
        logger.info("MarkLogic port: " + appCfg.mlPort());
        logger.info("MarkLogic user: " + appCfg.mlUser());
        logger.info("MarkLogic password: " + appCfg.mlPassword());
        logger.info("MarkLogic DB: " + appCfg.mlDB());
    }

    @Override
    public LDAPResult authenticate(BindRequestProtocolOp request, LDAPResult result, String token) {
        logger.info("MLGoogle Authenticator called.");
        LDAPResult bindResult = result;
        String userid = null;

        try {
            DN dn = new DN(request.getBindDN());
            if (dn.matchesBaseAndScope(appCfg.mlBaseDn(), SearchScope.SUB)) {
                logger.info("Checking userid with MLGoogle Authenticator Servers");
                for (String r : dn.getRDNStrings()) {
                    logger.debug("RDN: " + r);
                    RDN rdn = new RDN(r);
                    if (rdn.hasAttribute(appCfg.mlUseridAttribute())) {
                        logger.debug("Found Userid Attribute ");
                        userid = rdn.getAttributeValues()[0];
                        logger.debug("Userid Attribute value " + rdn.getAttributeValues()[0]);
                    }
                }
            } else {
                logger.info("Bypassing MLGoogle Authenticator for User DN");
                return bindResult;
            }
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
        }
        // If no userid attribute is found bail out with an Invalid Syntax error
        // In theory this shouldn't happen but ya never know.
        if (userid == null) {
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(), ResultCode.INVALID_DN_SYNTAX, "Unable to locate userid attribute " + appCfg.mlUseridAttribute() + "in user DN.", result.getMatchedDN(), result.getReferralURLs(), result.getResponseControls());
            return failedBindResult;
        }
        if (token == null) {
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(), ResultCode.AUTHORIZATION_DENIED, "Google Authenticator Token missing.", result.getMatchedDN(), result.getReferralURLs(), result.getResponseControls());
            return failedBindResult;
        }

        logger.info("Authenticating userid: " + userid);
        logger.debug("Authenticate token: " + new Integer(token).intValue());

        String secretKey="";
        DatabaseClient client=null;
        try {
            // There are probably a lot better ways to do this and the connection should definately be encrypted.
            client = DatabaseClientFactory.newClient(appCfg.mlHost(), appCfg.mlPort(), appCfg.mlDB(), appCfg.mlUser(), appCfg.mlPassword(), DatabaseClientFactory.Authentication.BASIC);
            ServerEvaluationCall seCall = client.newServerEval();
            seCall.javascript("cts.doc(\"/martin.json\").root.key");
            secretKey = seCall.evalAs(String.class);
            logger.debug("Secret key: " + secretKey);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
            e.printStackTrace();
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(),ResultCode.AUTHORIZATION_DENIED ,e.getLocalizedMessage(),result.getMatchedDN(),result.getReferralURLs(),result.getResponseControls());
            return failedBindResult;
        } finally {
            client.release();
        }

        try {
            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            int code = gAuth.getTotpPassword(secretKey);
            logger.debug("Current TOTP Token for user: " + String.valueOf(code));

            boolean isCodeValid = gAuth.authorize(secretKey, new Integer(token).intValue());
            logger.debug("IsCodeValid: " + isCodeValid);
            if (!isCodeValid) {
                logger.error("Invalid token entered.");
                LDAPResult failedBindResult = new LDAPResult(result.getMessageID(), ResultCode.AUTHORIZATION_DENIED, "Invalid token entered.", result.getMatchedDN(), result.getReferralURLs(), result.getResponseControls());
                return failedBindResult;
            }
            logger.info("User loggin successful!");
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
            e.printStackTrace();
            LDAPResult failedBindResult = new LDAPResult(result.getMessageID(), ResultCode.AUTHORIZATION_DENIED, e.getLocalizedMessage(), result.getMatchedDN(), result.getReferralURLs(), result.getResponseControls());
            return failedBindResult;
        }

        return bindResult;
    }

    @Override
    public SearchResult search(int messageID, SearchRequest request, LDAPListenerClientConnection listenerConnection) {
        return null;
    }

}
