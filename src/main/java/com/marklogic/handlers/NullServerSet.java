package com.marklogic.handlers;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ServerSet;
import com.unboundid.util.NotMutable;
import com.unboundid.util.ThreadSafety;
import com.unboundid.util.ThreadSafetyLevel;

import javax.net.SocketFactory;

/**
 * Created by mwarnes on 30/06/2017.
 */
@NotMutable
@ThreadSafety(
        level = ThreadSafetyLevel.COMPLETELY_THREADSAFE
)
public final class NullServerSet extends ServerSet {

    public NullServerSet() {

    }

    @Override
    public LDAPConnection getConnection() throws LDAPException {
       return new LDAPConnection();
    }
}

