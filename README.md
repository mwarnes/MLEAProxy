# MLEAProxy
## An External LDAP Authentication Proxy server for MarkLogic Server
### Introduction
MLEAProxy was primarily written as a support tool to aide in diagnosing issue with authorising MarkLogic Users against an external LDAP or Active Directory server.
<P>As well a being a proxy LDAP server MLEAProxy can run as simple LDAP server using an XML file containing a psuedo LDAP configuration. This is useful for users wanting to configure and evaluate MarkLogic External Security without the need to access a full LDAP or Active Directory server. This is the default configuration mode if no properties file is available.
<P>MLEXProxy can also be extended using custom written Java code to intercept LDAP requests and take any actions or make modifications before returning the response to the MarkLogic server. the builtin LDAP XML Server us a Custom written authenticator class.

### Installation

 [<img src="./download.png" width="160">](./mleaproxy.jar) or clone the repository and build your own version.
 
 <p>To run simply execute the the runnable Jar file
 
```` 
java -jar mleaproxy.jar 



````

MLEAProxy will search in the following order of preference for a mleaproxy.properties file in the current directory, the users home directory, /etc/ directory on Unix and finally will use the default in-built properties configuration if no other is found.

````
./mleaproxy.properties
${HOME}/mleaproxy.properties
/etc/mleaproxy.properties
Application defaults
````

### Configuration

The configuration is made up of 4 areas, Servers, Server Sets, Listeners and Authenticators.

Servers define the back-end LDAP or Active Directory server that MLEAProxy will connect to.

````
Parameters
==========
ldapserver.<name>.host : IP Address or Hostname of back-end LDAP server <Required>.
ldapserver.<name>.port : Back-end LDAP Server listening port <Required>.
 
Examples
======== 
ldapserver.server1.host=dirsrv1.marklogic.com
ldapserver.server1.port=389

ldapserver.server2.host=dirsrv2.marklogic.com
ldapserver.server2.port=389
 
ldapserver.ad1.host=192.168.0.60
ldapserver.ad1.port=636
````

Server Sets define the back-end LDAP servers to use; As well as single a back-end server, MLEAProxy supports load balancing to one or more back-end LDAP servers

````
Parameters
==========
ldapset.<set name>.servers        : The names of one or more ldapserver configurations <Required>.
ldapset.<set name>.secure         : True/False if Secure connection is required (Default:false) <Optional>.
ldapset.<set name>.keystore       : Path to Java keystore containing user certificate if remote server requires
                                    TLS Client Autnentication <Optional>.
ldapset.<set name>.keystorepasswd : Keystore password <Required only if keystore specified>.
ldapset.<set name>.truststore     : Path to Java Truststore containing CA certificates, if not specified and a secure
                                    session is opened a TrustAll policy will be used.
ldapset.<set name>.truststorepasswd : Truststore password <Required only if Truststorestore specified>.
 
Examples
========  
ldapset.set1.servers=server1
 
ldapset.set2.servers=server1,server2
 
ldapset.set3.servers=ad1
ldapset.set3.secure=true
ldapset.set3.truststore=/Users/mwarnes/mlproxy.jks
ldapset.set3.truststorepasswd=password
````

Authenticators are used to identify the Authenticator and Configuration classes that will be used to handle the request using Custom classes.

````
Parameters
==========
authenticator.xmlauthenticator.authclass   : Java class to process the request <Required>.
authenticator.xmlauthenticator.configclass : Java class to process configuration <Required>.
authenticator.xmlauthenticator.parm[1-20]  : Optional list of parameters (Maximum 20 pararmeters).
 
Examples
======== 
authenticator.xmlauthenticator.authclass=com.marklogic.authenticators.XMLAuthenticator
authenticator.xmlauthenticator.configclass=com.marklogic.configuration.CustomConfig
authenticator.xmlauthenticator.parm1=/path/to/users.xml 
````

A Listener defines the individual MLEAProxy listening instance and defines such properties as listening port, binnd address, whether TLS is required, the ldapserver set to use and the type of load balancing if required.

````
Parameters
==========
listener.<name>.ipaddress       : IP address to bind to (Default 0.0.0.0). 
listener.<name>.port            : port to listenon <Required>.
listener.<name>.debugleve       : Level to use INFO|WARN|ERROR|TRACE|DEBUG  (Default INFO)
listener.<name>.requestHandler  : Request handler for incoming connections, currently the following are supported:
 
                                  com.marklogic.handlers.ProxyRequestHandler
                                  com.marklogic.handlers.CustomRequestHandler
                                   
listener.<name>.authenticator   : Authentcator definition to use if CustomRequestHandler is used.                              
listener.<name>.secure          : True/False whether to enable TLS (Default false).
listener.<name>.keystore        : Java Keystore containing system certificate.
listener.<name>.keystorepasswd  : Keystore password
listener.<name>.ldapset         : Name of LDAP Server set to use.
listener.<name>.ldapmode        : LDAP balancing mode (single|failover|roundrobin|roundrobindns|fewest|fastest) (Default single).
listener.<name>.description     : Description of listener (Optional).
 
Examples
======== 
listener.proxy.ipaddress=0.0.0.0
listener.proxy.port=30389
listener.proxy.secure=false
listener.proxy.requestHandler=com.marklogic.handlers.ProxyRequestHandler
listener.proxy.ldapset=set2
listener.proxy.ldapmode=roundrobin
listener.proxy.description=General LDAP proxy with round robin load balancing to 2 back-end servers.
 
listener.xmlcustom.ipaddress=0.0.0.0
listener.xmlcustom.port=20389
listener.xmlcustom.requestHandler=com.marklogic.handlers.CustomRequestHandler
listener.xmlcustom.authenticator=xmlauthenticator
listener.xmlcustom.ldapset=set1
listener.xmlcustom.ldapmode=single
listener.xmlcustom.description=LDAP server with Custom Authenticator using XML backing store
````

MLEAProxy is able to run multiple, Listeners configure which listener instances will be started

````
Parameters
==========
listeners   : Name of listeners to start
 
Examples
======== 
listeners=proxy
 
listeners=proxy,xmlcustom
````

Lower level LDAP debugging in addition to the listener debug level can be enabled using the following parameter in the mleaproxy.properies file.

````
ldap.debug=true
````

### Sample Configurations
#### Simple LDAP Proxy server
The following configuration will start a simple proxy listener to relay LDAP request back and forth between a client such as a MarkLogic server and a back-end LDAP server. In addition both LDAP and Listener debugging is enabled to write detailed LDAP Request/Response information.

<img src="./simpleproxy.png">

````
ldap.debug=true
## Listeners
listeners=proxy
## Listener
listener.proxy.ipaddress=0.0.0.0
listener.proxy.port=30389
listener.proxy.debuglevel=DEBUG
listener.proxy.secure=false
listener.proxy.requestHandler=com.marklogic.handlers.ProxyRequestHandler
listener.proxy.ldapset=set1
listener.proxy.ldapmode=single
listener.proxy.description=Simple LDAP proxy
## LDAP Server set
ldapset.set1.servers=server1
## LDAP Server
ldapserver.server1.host=kerberos.marklogic.local
ldapserver.server1.port=10389
````
Sample log output

````
2017-06-30 12:55:23.157  INFO 86215 --- [           main] com.marklogic.MLEAProxy                  : Starting MLEAProxy on MacPro-4505.local with PID 86215 (/Users/mwarnes/IdeaProjects/MLEAProxy/target/classes started by mwarnes in /Users/mwarnes/IdeaProjects/MLEAProxy)
2017-06-30 12:55:23.159  INFO 86215 --- [           main] com.marklogic.MLEAProxy                  : No active profile set, falling back to default profiles: default
2017-06-30 12:55:23.200  INFO 86215 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@7e0e6aa2: startup date [Fri Jun 30 12:55:23 BST 2017]; root of context hierarchy
2017-06-30 12:55:24.001  INFO 86215 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2017-06-30 12:55:24.030 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : IP Address: 0.0.0.0
2017-06-30 12:55:24.031 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : Port: 30389
2017-06-30 12:55:24.031 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : Request handler: com.marklogic.handlers.ProxyRequestHandler
2017-06-30 12:55:24.031 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 12:55:24.032 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : Building server sets
2017-06-30 12:55:24.032 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 12:55:24.035 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: kerberos.marklogic.local
2017-06-30 12:55:24.036 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 389
2017-06-30 12:55:24.039 DEBUG 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : SingleServerSet(server=kerberos.marklogic.local:389)
2017-06-30 12:55:24.045  INFO 86215 --- [           main] com.marklogic.handlers.LDAPlistener      : Listening on: 0.0.0.0:30389 ( Simple LDAP proxy )
2017-06-30 12:55:24.047  INFO 86215 --- [           main] com.marklogic.MLEAProxy                  : Started MLEAProxy in 1.216 seconds (JVM running for 1.494)
2017-06-30 12:55:37.171  INFO 86215 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=17 threadName="LDAPListener client connection reader for connection from 127.0.0.1:52578 to 127.0.0.1:30389" revision=24201 sendingLDAPRequest="SimpleBindRequest(dn='cn=Manager,dc=MarkLogic,dc=Local')"
2017-06-30 12:55:37.182  INFO 86215 --- [logic.local:389] com.unboundid.ldap.sdk                   : level="INFO" threadID=19 threadName="Connection reader for connection 0 to kerberos.marklogic.local:389" revision=24201 connectionID=0 connectedTo="kerberos.marklogic.local:389" readLDAPResult="BindResult(resultCode=0 (success), messageID=1, hasServerSASLCredentials=false)"
2017-06-30 12:55:37.191  INFO 86215 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=17 threadName="LDAPListener client connection reader for connection from 127.0.0.1:52578 to 127.0.0.1:30389" revision=24201 sendingLDAPRequest="SearchRequest(baseDN='', scope=BASE, deref=ALWAYS, sizeLimit=0, timeLimit=0, filter='(objectClass=*)', attrs={subschemaSubentry})"
2017-06-30 12:55:37.194  INFO 86215 --- [logic.local:389] com.unboundid.ldap.sdk                   : level="INFO" threadID=19 threadName="Connection reader for connection 0 to kerberos.marklogic.local:389" revision=24201 connectionID=0 connectedTo="kerberos.marklogic.local:389" readLDAPResult="SearchResultEntry(dn='', messageID=2, attributes={Attribute(name=subschemaSubentry, values={'cn=Subschema'})}, controls={})"
2017-06-30 12:55:37.194  INFO 86215 --- [logic.local:389] com.unboundid.ldap.sdk                   : level="INFO" threadID=19 threadName="Connection reader for connection 0 to kerberos.marklogic.local:389" revision=24201 connectionID=0 connectedTo="kerberos.marklogic.local:389" readLDAPResult="SearchResult(resultCode=0 (success), messageID=2)"

````

#### Secure LDAP Proxy server (1)

An example configuration building on the simple LDAP proxy but securing the back-end connection to the LDAP server using LDAPS security.
This is a useful configuration for diagnosing external security problems where the back-end LDAP server requires a TLS encrypted session. 
With the MLEAProxy in place LDAP traffic can be seen in the output display and also makes it possible to capture network traffic between the client and MLEAProxy for further diagnosis using the Wireshark LDAP dissector.

<img src="./secureproxy1.png">

````
ldap.debug=true
## Listeners
listeners=proxy
## Listener
listener.proxy.ipaddress=0.0.0.0
listener.proxy.port=30389
listener.proxy.debuglevel=DEBUG
listener.proxy.secure=false
listener.proxy.requestHandler=com.marklogic.handlers.ProxyRequestHandler
listener.proxy.ldapset=set1
listener.proxy.ldapmode=single
listener.proxy.description=LDAP proxy with LDAPS connection to back-end server.
## LDAP Server set
ldapset.set1.servers=server1
ldapset.set1.secure=true
## LDAP Server
ldapserver.server1.host=kerberos.marklogic.local
ldapserver.server1.port=636
````

Sample output

````
2017-06-30 14:02:47.259  INFO 89193 --- [           main] com.marklogic.MLEAProxy                  : Starting MLEAProxy on MacPro-4505.local with PID 89193 (/Users/mwarnes/IdeaProjects/MLEAProxy/target/classes started by mwarnes in /Users/mwarnes/IdeaProjects/MLEAProxy)
2017-06-30 14:02:48.035 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : IP Address: 0.0.0.0
2017-06-30 14:02:48.035 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Port: 30389
2017-06-30 14:02:48.035 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Request handler: com.marklogic.handlers.ProxyRequestHandler
2017-06-30 14:02:48.036 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 14:02:48.036 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Building server sets
2017-06-30 14:02:48.036 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 14:02:48.040 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: kerberos.marklogic.local
2017-06-30 14:02:48.040 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 636
2017-06-30 14:02:48.041 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Creating SSL Socket Factory.
2017-06-30 14:02:48.042 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Creating SSLUtil.
2017-06-30 14:02:48.042 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Keystore: 
2017-06-30 14:02:48.042 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Keystore password: 
2017-06-30 14:02:48.042 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Truststore: 
2017-06-30 14:02:48.042 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Truststore password: 
2017-06-30 14:02:48.042 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Using default TrustAllTrustManager.
2017-06-30 14:02:48.210 DEBUG 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : SingleServerSet(server=kerberos.marklogic.local:636)
2017-06-30 14:02:48.216  INFO 89193 --- [           main] com.marklogic.handlers.LDAPlistener      : Listening on: 0.0.0.0:30389 ( LDAP proxy with LDAPS connection to back-end server )
2017-06-30 14:02:48.217  INFO 89193 --- [           main] com.marklogic.MLEAProxy                  : Started MLEAProxy in 1.322 seconds (JVM running for 1.583)
2017-06-30 14:04:58.903  INFO 89193 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=17 threadName="LDAPListener client connection reader for connection from 127.0.0.1:53989 to 127.0.0.1:30389" revision=24201 sendingLDAPRequest="SimpleBindRequest(dn='cn=Manager,dc=MarkLogic,dc=Local')"
2017-06-30 14:04:59.311  INFO 89193 --- [logic.local:636] com.unboundid.ldap.sdk                   : level="INFO" threadID=19 threadName="Connection reader for connection 0 to kerberos.marklogic.local:636" revision=24201 connectionID=0 connectedTo="kerberos.marklogic.local:636" readLDAPResult="BindResult(resultCode=0 (success), messageID=1, hasServerSASLCredentials=false)"
````

This configuration can be further enhanced for cases where a TrustAll TrustManager is not acceptable by adding a user created truststore containing the required CA certificates.

````
ldapset.set1.truststore=/path/to/mlproxytrust.jks
ldapset.set1.truststorepasswd=password
````

If the back-end LDAPS server also requires TLS Client Authentication then a user created keystore containing a certificate and private key can be added.

````
ldapset.set1.keystore=/path/to/mlproxykey.jks
ldapset.set1.keystorepasswd=password
````

#### Secure LDAP Proxy server (2)

The following configuration add TLS Secure transport support to the MLEAProxy listening port, with this both front-end and back-end connection will user LDAPS.

<img src="./secureproxy2.png">

````
ldap.debug=true
## Listeners
listeners=proxy
## Listener
listener.proxy.ipaddress=0.0.0.0
listener.proxy.port=30636
listener.proxy.debuglevel=DEBUG
listener.proxy.secure=true
listener.proxy.keystore=/Users/mwarnes/mlproxy.jks
listener.proxy.keystorepasswd=password
listener.proxy.requestHandler=com.marklogic.handlers.ProxyRequestHandler
listener.proxy.ldapset=set1
listener.proxy.ldapmode=single
listener.proxy.description=LDAP proxy with LDAPS connection to front-end and back-end servers.
## LDAP Server set
ldapset.set1.servers=server1
ldapset.set1.secure=true
## LDAP Server
ldapserver.server1.host=kerberos.marklogic.local
ldapserver.server1.port=636
````

Sample output

````
2017-06-30 14:33:42.957  INFO 90645 --- [           main] com.marklogic.MLEAProxy                  : Starting MLEAProxy on MacPro-4505.local with PID 90645 (/Users/mwarnes/IdeaProjects/MLEAProxy/target/classes started by mwarnes in /Users/mwarnes/IdeaProjects/MLEAProxy)
2017-06-30 14:33:43.724 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : IP Address: 0.0.0.0
2017-06-30 14:33:43.725 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Port: 30636
2017-06-30 14:33:43.725 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Request handler: com.marklogic.handlers.ProxyRequestHandler
2017-06-30 14:33:43.725 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 14:33:43.726 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Building server sets
2017-06-30 14:33:43.726 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 14:33:43.729 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: kerberos.marklogic.local
2017-06-30 14:33:43.730 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 636
2017-06-30 14:33:43.730 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Creating SSL Socket Factory.
2017-06-30 14:33:43.731 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Creating SSLUtil.
2017-06-30 14:33:43.731 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Keystore: 
2017-06-30 14:33:43.731 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Keystore password: 
2017-06-30 14:33:43.731 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Truststore: 
2017-06-30 14:33:43.731 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Truststore password: 
2017-06-30 14:33:43.731 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Using default TrustAllTrustManager.
2017-06-30 14:33:43.882 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : SingleServerSet(server=kerberos.marklogic.local:636)
2017-06-30 14:33:43.886 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Creating Server Socket Factory.
2017-06-30 14:33:43.888 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Creating SSLUtil.
2017-06-30 14:33:43.888 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Keystore: /Users/mwarnes/mlproxy.jks
2017-06-30 14:33:43.888 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Keystore password: password
2017-06-30 14:33:43.888 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Truststore: 
2017-06-30 14:33:43.888 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Truststore password: 
2017-06-30 14:33:43.892 DEBUG 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : No Trust managers created using defined KeyManager & TrustAllTrustManager.
2017-06-30 14:33:43.897  INFO 90645 --- [           main] com.marklogic.handlers.LDAPlistener      : Listening on: 0.0.0.0:30636 ( Simple LDAP proxy )
2017-06-30 14:33:43.899  INFO 90645 --- [           main] com.marklogic.MLEAProxy                  : Started MLEAProxy in 1.262 seconds (JVM running for 1.524)
2017-06-30 14:34:34.413  INFO 90645 --- [127.0.0.1:30636] com.unboundid.ldap.sdk                   : level="INFO" threadID=17 threadName="LDAPListener client connection reader for connection from 127.0.0.1:54351 to 127.0.0.1:30636" revision=24201 sendingLDAPRequest="SimpleBindRequest(dn='cn=Manager,dc=MarkLogic,dc=Local')"
2017-06-30 14:34:34.428  INFO 90645 --- [logic.local:636] com.unboundid.ldap.sdk                   : level="INFO" threadID=19 threadName="Connection reader for connection 0 to kerberos.marklogic.local:636" revision=24201 connectionID=0 connectedTo="kerberos.marklogic.local:636" readLDAPResult="BindResult(resultCode=0 (success), messageID=1, hasServerSASLCredentials=false)"

````

#### Load balancing LDAP Proxy server (1)

MLEAProxy can also be configured as a load balancing proxy to handle a number of different scenerios including failover.

For this configuration MLEAProxy will balance between 3 back-end LDAP servers using a simple roundrobin algorithm

<img src="./loadbalance1.png">

````
ldap.debug=true
## Listeners
listeners=proxy
## Listener
listener.proxy.ipaddress=0.0.0.0
listener.proxy.port=30389
listener.proxy.debuglevel=DEBUG
listener.proxy.secure=false
listener.proxy.requestHandler=com.marklogic.handlers.ProxyRequestHandler
listener.proxy.ldapset=set1
listener.proxy.ldapmode=roundrobin
listener.proxy.description=General load balancing LDAP proxy.
## LDAP Server set
ldapset.set1.servers=server1,server2,server3
## LDAP Server
ldapserver.server1.host=192.168.0.50
ldapserver.server1.port=10389
ldapserver.server2.host=192.168.0.51
ldapserver.server2.port=10389
ldapserver.server3.host=192.168.0.52
ldapserver.server3.port=10389
````

Sample output showing 3 bind requests directing to 3 back-end servers in turn.

````
2017-06-30 15:58:26.855  INFO 94596 --- [           main] com.marklogic.MLEAProxy                  : Starting MLEAProxy on MacPro-4505.local with PID 94596 (/Users/mwarnes/IdeaProjects/MLEAProxy/target/classes started by mwarnes in /Users/mwarnes/IdeaProjects/MLEAProxy)
2017-06-30 15:58:27.636 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : IP Address: 0.0.0.0
2017-06-30 15:58:27.636 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : Port: 30389
2017-06-30 15:58:27.636 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : Request handler: com.marklogic.handlers.ProxyRequestHandler
2017-06-30 15:58:27.637 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 15:58:27.637 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : Building server sets
2017-06-30 15:58:27.637 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 15:58:27.641 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: 192.168.0.50
2017-06-30 15:58:27.641 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 10389
2017-06-30 15:58:27.642 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: 192.168.0.51
2017-06-30 15:58:27.643 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 10389
2017-06-30 15:58:27.643 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: 192.168.0.52
2017-06-30 15:58:27.644 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 10389
2017-06-30 15:58:27.647 DEBUG 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : RoundRobinServerSet(servers={192.168.0.50:10389, 192.168.0.51:10389, 192.168.0.52:10389})
2017-06-30 15:58:27.653  INFO 94596 --- [           main] com.marklogic.handlers.LDAPlistener      : Listening on: 0.0.0.0:30389 ( General load balancing LDAP proxy. )
2017-06-30 15:58:27.654  INFO 94596 --- [           main] com.marklogic.MLEAProxy                  : Started MLEAProxy in 1.181 seconds (JVM running for 1.463)
2017-06-30 15:59:40.119  INFO 94596 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=17 threadName="LDAPListener client connection reader for connection from 127.0.0.1:55788 to 127.0.0.1:30389" revision=24201 sendingLDAPRequest="SimpleBindRequest(dn='cn=Directory Manager')"
2017-06-30 15:59:40.131  INFO 94596 --- [.168.0.50:10389] com.unboundid.ldap.sdk                   : level="INFO" threadID=19 threadName="Connection reader for connection 0 to 192.168.0.50:10389" revision=24201 connectionID=0 connectedTo="192.168.0.50:10389" readLDAPResult="BindResult(resultCode=0 (success), messageID=1, hasServerSASLCredentials=false)"
2017-06-30 15:59:40.137  INFO 94596 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=17 threadName="LDAPListener client connection reader for connection from 127.0.0.1:55788 to 127.0.0.1:30389" revision=24201 message="Sending LDAP unbind request."
2017-06-30 15:59:44.346  INFO 94596 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=20 threadName="LDAPListener client connection reader for connection from 127.0.0.1:55790 to 127.0.0.1:30389" revision=24201 sendingLDAPRequest="SimpleBindRequest(dn='cn=Directory Manager')"
2017-06-30 15:59:44.347  INFO 94596 --- [.168.0.51:10389] com.unboundid.ldap.sdk                   : level="INFO" threadID=22 threadName="Connection reader for connection 1 to 192.168.0.51:10389" revision=24201 connectionID=1 connectedTo="192.168.0.51:10389" readLDAPResult="BindResult(resultCode=0 (success), messageID=1, hasServerSASLCredentials=false)"
2017-06-30 15:59:44.348  INFO 94596 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=20 threadName="LDAPListener client connection reader for connection from 127.0.0.1:55790 to 127.0.0.1:30389" revision=24201 message="Sending LDAP unbind request."
2017-06-30 15:59:47.384  INFO 94596 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=23 threadName="LDAPListener client connection reader for connection from 127.0.0.1:55795 to 127.0.0.1:30389" revision=24201 sendingLDAPRequest="SimpleBindRequest(dn='cn=Directory Manager')"
2017-06-30 15:59:47.385  INFO 94596 --- [.168.0.52:10389] com.unboundid.ldap.sdk                   : level="INFO" threadID=25 threadName="Connection reader for connection 2 to 192.168.0.52:10389" revision=24201 connectionID=2 connectedTo="192.168.0.52:10389" readLDAPResult="BindResult(resultCode=0 (success), messageID=1, hasServerSASLCredentials=false)"
2017-06-30 15:59:47.386  INFO 94596 --- [127.0.0.1:30389] com.unboundid.ldap.sdk                   : level="INFO" threadID=23 threadName="LDAPListener client connection reader for connection from 127.0.0.1:55795 to 127.0.0.1:30389" revision=24201 message="Sending LDAP unbind request."
````

In addition to the simple simple roundrobin load balancing algorithm, MLEAProxy also supports the following load balancing algorithms.

* <b>single</b>        : No load balancing performed only the first server in the set will be used.
* <b>failover</b>      : The first server in the set will be used while it is available, should the server become unavailable the next server in the set will be used.
* <b>roundrobin</b>    : Servers in the set will be used in turn.
* <b>roundrobindns</b> : Used when a hostname lookup returns multiple IP addresses, MLEAProxy will use each returned IP Address in turn the same as if they had been coded for roundrobin.
* <b>fewest</b>        : Servers in the set will be used on a least used basis, this is useful when variable LDAP request load result in one or more servers handling more work when roundrobin is used.
* <b>fastest</b>       : LDAP requests will be directed to the server that handles requests faster.

#### Load balancing LDAP Proxy server (2)

The following configuration is a more complex load balancing environment and shows how multiple server sets can be configured in a failover mode.

Each server set is configured to use 2 servers, each using a round robin algorithm, when multiple server sets are defined each set acts as the failover set to the previous one. This scenario would suit an environment where the primary LDAP servers are at site and a secondary set of backup LDAP servers at another. MLEAProxy will load balance between the 2 servers at the primary site and failover to load balancing to the secondary site should the primary site become unavailable.

<img src="./loadbalance2.png">

````
ldap.debug=true
## Listeners
listeners=proxy
## Listener
listener.proxy.ipaddress=0.0.0.0
listener.proxy.port=30389
listener.proxy.debuglevel=DEBUG
listener.proxy.secure=false
listener.proxy.requestHandler=com.marklogic.handlers.ProxyRequestHandler
listener.proxy.ldapset=set1,set2
listener.proxy.ldapmode=roundrobin
listener.proxy.description=Load balancing LDAP proxy with failover to secondary set.
## LDAP Server set
ldapset.set1.servers=server1,server2
ldapset.set2.servers=server3,server4
## LDAP Server
ldapserver.server1.host=192.168.0.50
ldapserver.server1.port=10389
ldapserver.server2.host=192.168.0.51
ldapserver.server2.port=10389
ldapserver.server3.host=192.168.0.52
ldapserver.server3.port=10389
ldapserver.server4.host=192.168.0.53
ldapserver.server4.port=10389
````

Sample output

````
2017-06-30 16:23:43.076  INFO 95738 --- [           main] com.marklogic.MLEAProxy                  : Starting MLEAProxy on MacPro-4505.local with PID 95738 (/Users/mwarnes/IdeaProjects/MLEAProxy/target/classes started by mwarnes in /Users/mwarnes/IdeaProjects/MLEAProxy)
2017-06-30 16:23:43.833 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : IP Address: 0.0.0.0
2017-06-30 16:23:43.834 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : Port: 30389
2017-06-30 16:23:43.834 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : Request handler: com.marklogic.handlers.ProxyRequestHandler
2017-06-30 16:23:43.834 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 16:23:43.834 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set2
2017-06-30 16:23:43.835 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : Building server sets
2017-06-30 16:23:43.835 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set1
2017-06-30 16:23:43.839 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: 192.168.0.50
2017-06-30 16:23:43.839 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 10389
2017-06-30 16:23:43.840 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: 192.168.0.51
2017-06-30 16:23:43.840 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 10389
2017-06-30 16:23:43.843 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : ServerSet: set2
2017-06-30 16:23:43.844 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: 192.168.0.52
2017-06-30 16:23:43.845 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 10389
2017-06-30 16:23:43.846 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server host: 192.168.0.53
2017-06-30 16:23:43.846 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : LDAP Server Port: 10389
2017-06-30 16:23:43.846 DEBUG 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : FailoverServerSet(serverSets={RoundRobinServerSet(servers={192.168.0.50:10389, 192.168.0.51:10389}), RoundRobinServerSet(servers={192.168.0.52:10389, 192.168.0.53:10389})})
2017-06-30 16:23:43.852  INFO 95738 --- [           main] com.marklogic.handlers.LDAPlistener      : Listening on: 0.0.0.0:30389 ( Load balancing LDAP proxy with failover to secondary set. )
2017-06-30 16:23:43.854  INFO 95738 --- [           main] com.marklogic.MLEAProxy                  : Started MLEAProxy in 1.096 seconds (JVM running for 1.36)
````

