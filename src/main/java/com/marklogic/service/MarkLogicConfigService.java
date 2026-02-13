package com.marklogic.service;

import com.marklogic.configuration.LDAPListenersConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * Service for generating MarkLogic External Security configuration files.
 * Creates JSON configuration files and instruction documents for setting up
 * LDAP authentication in MarkLogic Server.
 */
@Service
public class MarkLogicConfigService {

    private static final Logger logger = LoggerFactory.getLogger(MarkLogicConfigService.class);

    /**
     * Generates MarkLogic External Security configuration for an LDAP proxy listener.
     *
     * @param listenerName Name of the LDAP listener
     * @param listenerConfig Listener configuration
     * @throws IOException if file writing fails
     */
    public void generateConfigForListener(String listenerName, LDAPListenersConfig listenerConfig)
            throws IOException {
        logger.debug("Generating MarkLogic External Security config for listener: {}", listenerName);

        // Determine LDAP parameters
        String ldapBase = determineLdapBase(listenerConfig);
        String ldapAttribute = determineLdapAttribute(listenerConfig);

        // Build LDAP URI
        String protocol = listenerConfig.secureListener() ? "ldaps" : "ldap";
        String host = normalizeHostAddress(listenerConfig.listenerIpAddress());
        int port = listenerConfig.listenerPort();
        String ldapUri = String.format("%s://%s:%d", protocol, host, port);

        String description = String.format("External Security configuration for %s listener", listenerName);

        // Generate files
        String configJson = createExternalSecurityJson(listenerName, description, ldapUri, ldapBase, ldapAttribute);
        String configFileName = String.format("marklogic-external-security-%s.json", listenerName);
        writeFile(configFileName, configJson);

        String curlCommand = createCurlCommand(configFileName);
        String instructionFileName = String.format("marklogic-external-security-%s-instructions.txt", listenerName);
        createInstructionFile(instructionFileName, configFileName, listenerName, description,
                            ldapUri, ldapBase, ldapAttribute, curlCommand);

        logger.info("Generated MarkLogic External Security config: {} and {}", configFileName, instructionFileName);
    }

    /**
     * Generates MarkLogic External Security configuration for an in-memory LDAP server.
     *
     * @param serverName Name of the LDAP server
     * @param ipAddress IP address of the server
     * @param port Port number of the server
     * @param baseDN Base DN of the LDAP directory
     * @throws IOException if file writing fails
     */
    public void generateConfigForInMemoryServer(String serverName, String ipAddress, int port, String baseDN)
            throws IOException {
        logger.debug("Generating MarkLogic External Security config for in-memory server: {}", serverName);

        String ldapUri = String.format("ldap://%s:%d", normalizeHostAddress(ipAddress), port);
        String ldapAttribute = "uid";
        String description = String.format("External Security configuration for in-memory LDAP server %s", serverName);

        // Generate files
        String configJson = createExternalSecurityJson(serverName, description, ldapUri, baseDN, ldapAttribute);
        String configFileName = String.format("marklogic-external-security-%s.json", serverName);
        writeFile(configFileName, configJson);

        String curlCommand = createCurlCommand(configFileName);
        String instructionFileName = String.format("marklogic-external-security-%s-instructions.txt", serverName);
        createInstructionFile(instructionFileName, configFileName, serverName, description,
                            ldapUri, baseDN, ldapAttribute, curlCommand);

        logger.info("Generated MarkLogic External Security config: {} and {}", configFileName, instructionFileName);
    }

    /**
     * Determines the LDAP base DN based on listener configuration.
     */
    private String determineLdapBase(LDAPListenersConfig listenerConfig) {
        String processorName = listenerConfig.listenerRequestProcessor();
        String mode = listenerConfig.listenerLDAPMode();

        if ("JsonRequestProcessor".equals(processorName)) {
            return "ou=users,dc=marklogic,dc=local";
        } else if ("INTERNAL".equalsIgnoreCase(mode)) {
            return "dc=marklogic,dc=local";
        } else {
            return "ou=users,dc=company,dc=com";
        }
    }

    /**
     * Determines the LDAP user lookup attribute based on listener configuration.
     */
    private String determineLdapAttribute(LDAPListenersConfig listenerConfig) {
        String processorName = listenerConfig.listenerRequestProcessor();
        return "JsonRequestProcessor".equals(processorName) ? "sAMAccountName" : "uid";
    }

    /**
     * Normalizes IP address for use in LDAP URIs.
     * Converts 0.0.0.0 to localhost.
     */
    private String normalizeHostAddress(String address) {
        return "0.0.0.0".equals(address) ? "localhost" : address;
    }

    /**
     * Creates the MarkLogic External Security JSON configuration string.
     */
    private String createExternalSecurityJson(String name, String description, String ldapUri,
                                             String ldapBase, String ldapAttribute) {
        LocalDate today = LocalDate.now();
        String creationDate = today.toString();

        return String.format("{\n" +
            "  \"external-security-name\": \"%s\",\n" +
            "  \"description\": \"%s\",\n" +
            "  \"authentication\": \"ldap\",\n" +
            "  \"cache-timeout\": 300,\n" +
            "  \"authorization\": \"ldap\",\n" +
            "  \"ldap-server-uri\": \"%s\",\n" +
            "  \"ldap-base\": \"%s\",\n" +
            "  \"ldap-attribute\": \"%s\",\n" +
            "  \"ldap-default-user\": \"\",\n" +
            "  \"ldap-password\": \"\",\n" +
            "  \"ldap-bind-method\": \"simple\"\n" +
            "}\n" +
            "# Created: %s\n" +
            "# This configuration enables MarkLogic to authenticate users against the LDAP server.\n" +
            "# Use the MarkLogic Management REST API to import this configuration.\n",
            name, description, ldapUri, ldapBase, ldapAttribute, creationDate);
    }

    /**
     * Creates the curl command for importing the configuration.
     */
    private String createCurlCommand(String configFileName) {
        return String.format(
            "curl -X POST --anyauth --user admin:admin \\\n" +
            "  -H \"Content-Type: application/json\" \\\n" +
            "  -d @%s \\\n" +
            "  http://localhost:8002/manage/v2/external-security",
            configFileName);
    }

    /**
     * Creates the detailed instruction file for setting up external security.
     */
    private void createInstructionFile(String instructionFileName, String configFileName,
                                      String name, String description, String ldapUri,
                                      String ldapBase, String ldapAttribute, String curlCommand)
            throws IOException {
        LocalDate today = LocalDate.now();
        String creationDate = today.toString();

        StringBuilder instructions = new StringBuilder();
        instructions.append("================================================================================\n");
        instructions.append("MarkLogic External Security Configuration Instructions\n");
        instructions.append("================================================================================\n\n");
        instructions.append("Configuration Name: ").append(name).append("\n");
        instructions.append("Description: ").append(description).append("\n");
        instructions.append("Created: ").append(creationDate).append("\n\n");

        instructions.append("LDAP Server Details:\n");
        instructions.append("  URI: ").append(ldapUri).append("\n");
        instructions.append("  Base DN: ").append(ldapBase).append("\n");
        instructions.append("  User Attribute: ").append(ldapAttribute).append("\n\n");

        instructions.append("================================================================================\n");
        instructions.append("Step 1: Create External Security Configuration\n");
        instructions.append("================================================================================\n\n");
        instructions.append("Use the MarkLogic Management REST API to create the external security\n");
        instructions.append("configuration using the generated JSON file.\n\n");
        instructions.append("Command:\n");
        instructions.append(curlCommand).append("\n\n");

        instructions.append("================================================================================\n");
        instructions.append("Step 2: Configure App Server to Use External Security\n");
        instructions.append("================================================================================\n\n");
        instructions.append("1. Open MarkLogic Admin UI (http://localhost:8001)\n");
        instructions.append("2. Navigate to: Configure > Security > App Servers\n");
        instructions.append("3. Select your app server\n");
        instructions.append("4. Set 'authentication' to 'application-level'\n");
        instructions.append("5. Set 'external-security' to '").append(name).append("'\n");
        instructions.append("6. Click OK to save\n\n");

        instructions.append("OR use the Management REST API:\n\n");
        instructions.append("curl -X PUT --anyauth --user admin:admin \\\n");
        instructions.append("  -H \"Content-Type: application/json\" \\\n");
        instructions.append("  -d '{\"authentication\": \"application-level\", \"external-security\": \"").append(name).append("\"}' \\\n");
        instructions.append("  http://localhost:8002/manage/v2/servers/[server-name]/properties?group-id=[group]\n\n");

        instructions.append("================================================================================\n");
        instructions.append("Step 3: Test Authentication\n");
        instructions.append("================================================================================\n\n");
        instructions.append("Test with a valid LDAP user:\n\n");
        instructions.append("curl --anyauth --user [ldap-username]:[ldap-password] \\\n");
        instructions.append("  http://localhost:8000/\n\n");

        instructions.append("================================================================================\n");
        instructions.append("Troubleshooting\n");
        instructions.append("================================================================================\n\n");
        instructions.append("- Check ErrorLog.txt for LDAP connection errors\n");
        instructions.append("- Verify LDAP server is accessible from MarkLogic host\n");
        instructions.append("- Confirm Base DN and attribute names match your LDAP schema\n");
        instructions.append("- Test LDAP connectivity: ldapsearch -H ").append(ldapUri).append(" -b \"").append(ldapBase).append("\"\n\n");

        instructions.append("================================================================================\n");
        instructions.append("Additional Resources\n");
        instructions.append("================================================================================\n\n");
        instructions.append("MarkLogic External Security Documentation:\n");
        instructions.append("https://docs.marklogic.com/guide/security/external-auth\n\n");

        writeFile(instructionFileName, instructions.toString());
    }

    /**
     * Writes content to a file in the current directory.
     */
    private void writeFile(String fileName, String content) throws IOException {
        Path filePath = Paths.get(fileName);
        Files.writeString(filePath, content);
        logger.debug("Wrote file: {}", fileName);
    }
}
