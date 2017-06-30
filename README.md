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
ldapserver.server1.host=192.168.0.50
ldapserver.server1.port=10389
````
