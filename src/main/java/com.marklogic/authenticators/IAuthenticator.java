package com.marklogic.authenticators;

import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.sdk.LDAPResult;
import org.aeonbits.owner.Config;

import java.util.List;


/**
 * Created by mwarnes on 01/02/2017.
 */
public abstract interface IAuthenticator {

    public abstract void initialize(Config cfg) throws Exception;

    public abstract LDAPResult authenticate(BindRequestProtocolOp request, LDAPResult bindResult, String token);

}
