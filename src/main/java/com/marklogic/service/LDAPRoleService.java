package com.marklogic.service;

import com.unboundid.ldap.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service for querying LDAP for user group memberships and roles.
 * 
 * This service connects to the in-memory LDAP server (or external LDAP)
 * to retrieve user's group memberships and map them to application roles.
 * 
 * Features:
 * - Query LDAP for user's memberOf attributes
 * - Map LDAP groups to application roles
 * - Configurable group-to-role mapping
 * - Connection pooling for performance
 * - Cache support for frequently queried users
 * 
 * Configuration:
 * - ldap.role.query.enabled: Enable LDAP role queries
 * - ldap.role.query.host: LDAP server host
 * - ldap.role.query.port: LDAP server port
 * - ldap.role.query.base-dn: Base DN for user searches
 * - ldap.role.query.bind-dn: Admin DN for binding
 * - ldap.role.query.bind-password: Admin password
 * 
 * @since 2.0.0 (Phase 4)
 */
@Service
public class LDAPRoleService {
    private static final Logger logger = LoggerFactory.getLogger(LDAPRoleService.class);

    @Value("${ldap.role.query.enabled:false}")
    private boolean enabled;

    @Value("${ldap.role.query.host:localhost}")
    private String ldapHost;

    @Value("${ldap.role.query.port:60389}")
    private int ldapPort;

    @Value("${ldap.role.query.base-dn:dc=MarkLogic,dc=Local}")
    private String baseDN;

    @Value("${ldap.role.query.bind-dn:cn=Directory Manager}")
    private String bindDN;

    @Value("${ldap.role.query.bind-password:password}")
    private String bindPassword;

    @Value("${ldap.role.query.user-filter:(uid={0})}")
    private String userFilter;

    @Value("${ldap.role.query.group-attribute:memberOf}")
    private String groupAttribute;

    // Group to role mapping (configurable)
    // Format: ldapGroup=appRole,ldapGroup2=appRole2
    @Value("${ldap.role.mapping:}")
    private String roleMappingConfig;

    private Map<String, String> groupToRoleMapping;
    private LDAPConnectionPool connectionPool;
    private volatile boolean initialized = false;

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("LDAP role query service is disabled");
            return;
        }

        try {
            logger.info("Initializing LDAP role query service");
            logger.info("  LDAP Host: {}", ldapHost);
            logger.info("  LDAP Port: {}", ldapPort);
            logger.info("  Base DN: {}", baseDN);
            logger.info("  User Filter: {}", userFilter);

            // Parse role mapping configuration
            parseRoleMapping();

            // Create LDAP connection pool
            LDAPConnection connection = new LDAPConnection(ldapHost, ldapPort, bindDN, bindPassword);
            connectionPool = new LDAPConnectionPool(connection, 3, 10);

            // Test connection
            RootDSE rootDSE = connection.getRootDSE();
            logger.info("LDAP connection successful, server: {}", 
                rootDSE != null ? rootDSE.getVendorName() : "Unknown");

            initialized = true;
            logger.info("LDAP role query service initialized successfully");

        } catch (LDAPException e) {
            logger.error("Failed to initialize LDAP role query service", e);
            logger.warn("LDAP role queries will be disabled");
            initialized = false;
        }
    }

    /**
     * Parse role mapping configuration.
     * Format: "cn=app-readers,ou=groups,dc=marklogic,dc=local=app-reader,cn=admins,ou=groups,dc=marklogic,dc=local=admin"
     */
    private void parseRoleMapping() {
        groupToRoleMapping = new HashMap<>();
        
        if (roleMappingConfig == null || roleMappingConfig.trim().isEmpty()) {
            logger.debug("No role mapping configuration provided, using default mapping");
            // Default mapping: extract CN from group DN
            return;
        }

        logger.debug("Parsing role mapping configuration: {}", roleMappingConfig);
        
        String[] mappings = roleMappingConfig.split(",");
        for (String mapping : mappings) {
            String[] parts = mapping.split("=", 2);
            if (parts.length == 2) {
                String groupDN = parts[0].trim();
                String roleName = parts[1].trim();
                groupToRoleMapping.put(groupDN.toLowerCase(), roleName);
                logger.debug("  Mapped: {} -> {}", groupDN, roleName);
            }
        }

        logger.info("Loaded {} LDAP group-to-role mappings", groupToRoleMapping.size());
    }

    /**
     * Query LDAP for user's roles based on group memberships.
     * 
     * @param username Username to query
     * @return List of roles, or empty list if user not found or error occurs
     */
    public List<String> getUserRoles(String username) {
        if (!initialized) {
            logger.debug("LDAP role service not initialized, returning empty roles");
            return Collections.emptyList();
        }

        try {
            logger.debug("Querying LDAP for roles of user: {}", username);

            // Build search filter
            String filter = userFilter.replace("{0}", username);
            
            // Search for user
            SearchRequest searchRequest = new SearchRequest(
                baseDN,
                SearchScope.SUB,
                filter,
                groupAttribute, "cn", "uid"
            );

            SearchResult searchResult = connectionPool.search(searchRequest);
            
            if (searchResult.getEntryCount() == 0) {
                logger.debug("User not found in LDAP: {}", username);
                return Collections.emptyList();
            }

            SearchResultEntry entry = searchResult.getSearchEntries().get(0);
            logger.debug("Found LDAP entry: {}", entry.getDN());

            // Extract group memberships
            String[] groups = entry.getAttributeValues(groupAttribute);
            if (groups == null || groups.length == 0) {
                logger.debug("User {} has no group memberships", username);
                return Collections.emptyList();
            }

            logger.debug("User {} is member of {} groups", username, groups.length);

            // Map groups to roles
            List<String> roles = new ArrayList<>();
            for (String groupDN : groups) {
                String role = mapGroupToRole(groupDN);
                if (role != null) {
                    roles.add(role);
                    logger.debug("  Group {} mapped to role: {}", groupDN, role);
                }
            }

            logger.info("User {} has {} roles from LDAP: {}", username, roles.size(), roles);
            return roles;

        } catch (LDAPException e) {
            logger.error("Error querying LDAP for user roles: {}", username, e);
            return Collections.emptyList();
        }
    }

    /**
     * Map LDAP group DN to application role.
     * 
     * If explicit mapping exists, use it.
     * Otherwise, extract CN from group DN as role name.
     * 
     * @param groupDN LDAP group DN
     * @return Role name, or null if group should be ignored
     */
    private String mapGroupToRole(String groupDN) {
        // Check explicit mapping first
        String mappedRole = groupToRoleMapping.get(groupDN.toLowerCase());
        if (mappedRole != null) {
            return mappedRole;
        }

        // Default: extract CN from DN
        try {
            DN dn = new DN(groupDN);
            RDN firstRDN = dn.getRDN();
            if (firstRDN != null) {
                String[] attributeValues = firstRDN.getAttributeValues();
                if (attributeValues.length > 0) {
                    // Extract CN value (e.g., "app-readers" from "cn=app-readers,ou=groups,...")
                    String roleName = attributeValues[0];
                    logger.debug("Extracted role from CN: {}", roleName);
                    return roleName;
                }
            }
        } catch (LDAPException e) {
            logger.warn("Failed to parse group DN: {}", groupDN, e);
        }

        return null;
    }

    /**
     * Check if LDAP role service is initialized and ready.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get user attributes from LDAP.
     * 
     * @param username Username to query
     * @param attributes Attribute names to retrieve
     * @return Map of attribute names to values
     */
    public Map<String, List<String>> getUserAttributes(String username, String... attributes) {
        if (!initialized) {
            return Collections.emptyMap();
        }

        try {
            String filter = userFilter.replace("{0}", username);
            
            SearchRequest searchRequest = new SearchRequest(
                baseDN,
                SearchScope.SUB,
                filter,
                attributes
            );

            SearchResult searchResult = connectionPool.search(searchRequest);
            
            if (searchResult.getEntryCount() == 0) {
                return Collections.emptyMap();
            }

            SearchResultEntry entry = searchResult.getSearchEntries().get(0);
            
            Map<String, List<String>> attributeMap = new HashMap<>();
            for (String attrName : attributes) {
                String[] values = entry.getAttributeValues(attrName);
                if (values != null && values.length > 0) {
                    attributeMap.put(attrName, Arrays.asList(values));
                }
            }

            return attributeMap;

        } catch (LDAPException e) {
            logger.error("Error querying LDAP for user attributes: {}", username, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Shutdown the connection pool.
     */
    public void shutdown() {
        if (connectionPool != null) {
            connectionPool.close();
            logger.info("LDAP connection pool closed");
        }
    }
}
