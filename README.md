# MLEAProxy
##An External LDAP Authentication Proxy server for MarkLogic Server
### Introduction
MLEAProxy was primarily written as a support tool to aide in diagnosing issue with authorising MarkLogic Users against an external LDAP or Active Directory server.
<P>As well a being a proxy LDAP server MLEAProxy can run as simple LDAP server using an XML file containing a psuedo LDAP configuration. This is useful for users wanting to configure and evaluate MarkLogic External Security without the need to access a full LDAP or Active Directory server. This is the default configuration mode if no properties file is available.
<P>MLEXProxy can also be extended using custom written Java code to intercept LDAP requests and take any actions or make modifications before returning the response to the MarkLogic server.

### Installation

Download the latest version from <img src="./download.png" width="48">

c




