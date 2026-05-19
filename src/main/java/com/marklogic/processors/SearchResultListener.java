package com.marklogic.processors;

import com.marklogic.handlers.LDAPRequestHandler;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.protocol.SearchResultReferenceProtocolOp;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchResultReference;
import com.unboundid.util.Debug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


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

    // Map of Attributes to replace in Search response
    private final Map<String, String> responseMap;

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(SearchResultListener.class);

    /**
     * Creates a new search result listener with the provided information.
     *  @param  clientConnection  The client connection to which the results will
     *                           be sent.
     * @param  messageID         The message ID that will be used for any response
     * @param responseMap
     */
    SearchResultListener(final LDAPListenerClientConnection clientConnection,
                         final int messageID, final Map<String, String> responseMap)
    {
        this.clientConnection = clientConnection;
        this.messageID        = messageID;
        this.responseMap      = responseMap;
    }

    SearchResultListener(final LDAPListenerClientConnection clientConnection,
                         final int messageID)
    {
        this.clientConnection = clientConnection;
        this.messageID        = messageID;
        this.responseMap      = new HashMap<String, String>();
    }

    /**
     * {@inheritDoc}
     */
    public void searchEntryReturned(final SearchResultEntry searchEntry)
    {
        try
        {
            logger.debug(searchEntry.getAttributes().toString());
            if (!responseMap.isEmpty()) {
                // Loop through Attributes return and prcoess any response mappings.
                ArrayList retAttr = new ArrayList();
                for (Attribute a : searchEntry.getAttributes()) {
                    logger.debug("Attribute: " + a);
                    Attribute newAttribute = null;
                    if (responseMap.containsKey(a.getName())) {
                        logger.debug("Mapping: " + a.getName() + " to " + responseMap.get(a.getName()));
                        newAttribute = new Attribute(responseMap.get(a.getName()), a.getValues());
                        retAttr.add(newAttribute);
                        logger.debug("Mapped Attribute: " + newAttribute);
                    } else {
                        retAttr.add(a);
                    }
                }
                SearchResultEntry sre = new SearchResultEntry(messageID, searchEntry.getDN(), retAttr);
                clientConnection.sendSearchResultEntry(messageID, sre,
                        searchEntry.getControls());
            } else {
                clientConnection.sendSearchResultEntry(messageID, searchEntry,
                        searchEntry.getControls());
            }
        }
        catch (final Exception e)
        {
            logger.error(e.getMessage());
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
