package com.marklogic.configuration;

import com.marklogic.configuration.properties.MleaProxyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

/**
 * Configuration class that enables {@link MleaProxyProperties} and configures
 * property sources for MLEAProxy.
 *
 * <p>Property sources are loaded in the following order (lowest to highest priority):
 * <ol>
 *   <li>{@code classpath:mleaproxy.properties} - Default properties bundled with the application</li>
 *   <li>{@code /etc/mleaproxy.properties} - System-wide configuration</li>
 *   <li>{@code ${user.home}/mleaproxy.properties} - User-specific configuration</li>
 *   <li>{@code ./mleaproxy.properties} - Working directory configuration</li>
 *   <li>{@code ./ldap.properties} - LDAP-specific configuration</li>
 *   <li>{@code ./saml.properties} - SAML-specific configuration</li>
 *   <li>{@code ./oauth.properties} - OAuth-specific configuration</li>
 *   <li>{@code ./directory.properties} - Directory-specific configuration</li>
 *   <li>{@code ./kerberos.properties} - Kerberos-specific configuration</li>
 * </ol>
 *
 * <p>Spring Boot command line arguments ({@code --property=value}) and system properties
 * ({@code -Dproperty=value}) have the highest priority and will override all file-based
 * property sources.
 *
 * <p>All property sources are configured with {@code ignoreResourceNotFound = true},
 * allowing the application to start even if configuration files are missing.
 */
@Configuration
@EnableConfigurationProperties(MleaProxyProperties.class)
@PropertySources({
    @PropertySource(value = "classpath:mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:/etc/mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:${user.home}/mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./mleaproxy.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./ldap.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./saml.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./oauth.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./directory.properties", ignoreResourceNotFound = true),
    @PropertySource(value = "file:./kerberos.properties", ignoreResourceNotFound = true)
})
public class MleaProxyPropertiesConfig {
}
