package com.marklogic.authenticators;

import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import org.aeonbits.owner.Config;

import java.util.List;


/**
 * Created by mwarnes on 01/02/2017.
 */
public abstract interface IAuthenticator {

    public abstract void initialize(Config cfg) throws Exception;

    public abstract LDAPResult authenticate(BindRequestProtocolOp request, LDAPResult bindResult, String token);

    public abstract SearchResult search(int messageID, SearchRequest request, LDAPListenerClientConnection listenerConnection);

}
