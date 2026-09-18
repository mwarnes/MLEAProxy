package com.marklogic;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the application's LDAP servers on ephemeral ports for the annotated test class.
 *
 * <p>Every Spring test context loads {@code classpath:mleaproxy.properties} and its
 * {@code ApplicationRunner} starts the in-memory directory server and the LDAP proxy
 * listener. Those are configured on fixed ports (60389 and 10389), so any test class
 * with its own context cache key competes for them - with a locally running MLEAProxy
 * instance, with a concurrent build, and with other test contexts in the same JVM.
 * A failed bind surfaces as {@code Failed to load ApplicationContext}, which hides the
 * underlying {@code BindException} several levels down the cause chain.
 *
 * <p>Port 0 lets the OS assign a free port per context, so the suite no longer depends
 * on those ports being available. The HTTPS listener is switched off outright, since a
 * fixed 8443 in every test context would reintroduce the same contention.
 *
 * <p>Apply this to every {@code @SpringBootTest} class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@TestPropertySource(properties = {
        "mleaproxy.directory-servers.marklogic.port=0",
        "mleaproxy.ldap-listeners.proxy.port=0",
        "mleaproxy.https.enabled=false"
})
public @interface EphemeralServerPorts {
}
