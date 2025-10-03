package com.marklogic.processors;

import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.*;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPConnection;
import org.aeonbits.owner.Config;

import java.util.List;


/**
 * Created by mwarnes on 01/02/2017.
 */
public abstract interface IRequestProcessor {

    public abstract void initialize(Config cfg) throws Exception;

    public abstract LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);

    public abstract LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);

    public abstract LDAPMessage processAddRequest(int messageID, AddRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);

    public abstract LDAPMessage processCompareRequest(int messageID, CompareRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);

    public abstract LDAPMessage processDeleteRequest(int messageID, DeleteRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);

    public abstract LDAPMessage processExtendedRequest(int messageID, ExtendedRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);

    public abstract LDAPMessage processModifyRequest(int messageID, ModifyRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);

    public abstract LDAPMessage processModifyDNRequest(int messageID, ModifyDNRequestProtocolOp request, List<Control> controls, LDAPConnection ldapConnection, LDAPListenerClientConnection listenerConnection);



}
