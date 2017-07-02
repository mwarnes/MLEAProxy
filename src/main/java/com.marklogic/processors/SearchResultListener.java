package com.marklogic.processors;

import com.marklogic.handlers.LDAPRequestHandler;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.SearchResultReferenceProtocolOp;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchResultReference;
import com.unboundid.util.Debug;

import java.util.Arrays;


/**
 * This class provides an implementation of a search result listener that will
 * be used by the {@link LDAPRequestHandler} class in the course of returning
 * entries to the client.
 */
final class SearchResultListener
        implements com.unboundid.ldap.sdk.SearchResultListener
{
    /**
     * The serial version UID for this serializable class.
     */
    private static final long serialVersionUID = -1581507251328572490L;



    // The message ID for the associated search request.
    private final int messageID;

    // The client connection that will be used to return the results.
    private final LDAPListenerClientConnection clientConnection;



    /**
     * Creates a new search result listener with the provided information.
     *
     * @param  clientConnection  The client connection to which the results will
     *                           be sent.
     * @param  messageID         The message ID that will be used for any response
     *                           messages returned to the client.
     */
    SearchResultListener(final LDAPListenerClientConnection clientConnection,
                         final int messageID)
    {
        this.clientConnection = clientConnection;
        this.messageID        = messageID;
    }



    /**
     * {@inheritDoc}
     */
    public void searchEntryReturned(final SearchResultEntry searchEntry)
    {
        try
        {
            clientConnection.sendSearchResultEntry(messageID, searchEntry,
                    searchEntry.getControls());
        }
        catch (final Exception e)
        {
            Debug.debugException(e);
        }
    }



    /**
     * {@inheritDoc}
     */
    public void searchReferenceReturned(
            final SearchResultReference searchReference)
    {
        try
        {
            final SearchResultReferenceProtocolOp searchResultReferenceProtocolOp =
                    new SearchResultReferenceProtocolOp(Arrays.asList(
                            searchReference.getReferralURLs()));

            clientConnection.sendSearchResultReference(messageID,
                    searchResultReferenceProtocolOp, searchReference.getControls());
        }
        catch (final Exception e)
        {
            Debug.debugException(e);
        }
    }
}
