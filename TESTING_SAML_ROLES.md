# Testing SAML Role Attributes

## How to Test

### 1. Check Debug Logging is Enabled

Make sure your `mleaproxy.properties` file has:
```properties
saml.debug=true
```

### 2. Initiate SAML Authentication

Navigate to the SAML authentication endpoint with a SAMLRequest:
```
http://localhost:8080/saml/auth?SAMLRequest=<base64-encoded-request>
```

### 3. Fill in the Authentication Form

When the authentication form appears, enter:
- **userid**: `martin`
- **roles**: `admin,user,marklogic-admin` (comma-separated, no spaces or with spaces)
- **authn**: `SUCCESS`

Click Submit.

### 4. Check the Logs

You should see these log messages:

#### Before Processing Roles:
```
INFO  c.m.h.undertow.SAMLAuthHandler - Processing SAML authentication for user: martin
INFO  c.m.h.undertow.SAMLAuthHandler - Generating SAML response using OpenSAML 4.x for user: martin
DEBUG c.m.h.undertow.SAMLAuthHandler - Processing roles for user martin: [admin,user,marklogic-admin]
```

#### If Roles Are Added:
```
INFO  c.m.h.undertow.SAMLAuthHandler - Added 3 role(s) to SAML assertion: admin,user,marklogic-admin
INFO  c.m.h.undertow.SAMLAuthHandler - Assertion signature configured successfully
INFO  c.m.h.undertow.SAMLAuthHandler - Assertion signed successfully
```

#### If Roles Are NOT Added (Problem):
```
WARN  c.m.h.undertow.SAMLAuthHandler - No roles to add to SAML assertion for user: martin (roles value: '')
```

#### Debug Output (Full SAML Response):
```
DEBUG c.m.h.undertow.SAMLAuthHandler - Generated SAML Response XML (length: XXXX chars):
DEBUG c.m.h.undertow.SAMLAuthHandler - <saml2p:Response ...>...</saml2p:Response>
```

Then in the POST handler:
```
DEBUG c.m.h.undertow.SAMLAuthHandler - Generated SAML Response (Base64 decoded, length: XXXX chars):
DEBUG c.m.h.undertow.SAMLAuthHandler - <saml2p:Response ...>...</saml2p:Response>
```

#### Final Success Message:
```
INFO  c.m.h.undertow.SAMLAuthHandler - SAML authentication successful for user: martin with roles: admin,user,marklogic-admin
```

## Verifying the SAML Response Contains Roles

The debug output should show an `AttributeStatement` section like this:

```xml
<saml2:AttributeStatement>
  <saml2:Attribute Name="roles" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic">
    <saml2:AttributeValue xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                         xsi:type="xs:string">admin</saml2:AttributeValue>
    <saml2:AttributeValue xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                         xsi:type="xs:string">user</saml2:AttributeValue>
    <saml2:AttributeValue xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                         xsi:type="xs:string">marklogic-admin</saml2:AttributeValue>
  </saml2:Attribute>
</saml2:AttributeStatement>
```

## Troubleshooting

### Problem: "No roles to add" Warning

**Possible Causes:**
1. The `roles` parameter is empty or null
2. The form is not submitting the roles field correctly
3. The SAML bean is not retaining the roles value

**Solution:**
- Check the authentication form HTML to ensure it has an input field named `roles`
- Verify the roles parameter is being passed in the POST request
- Add logging to show the roles value before calling `generateSAMLResponseV4`

### Problem: Debug Logs Not Showing

**Possible Causes:**
1. `saml.debug=true` is not set in `mleaproxy.properties`
2. The logging level is set to INFO or higher

**Solution:**
- Verify `mleaproxy.properties` has `saml.debug=true`
- Check the application startup logs for the log level setting
- The log level is set dynamically at line 101 in SAMLAuthHandler.java

### Problem: AttributeStatement Missing from Response

**Possible Causes:**
1. Roles are null or empty when generating the response
2. An exception occurred during attribute creation

**Solution:**
- Check for the "No roles to add" warning in logs
- Check for any error messages during response generation
- Ensure the roles are set on the SAML bean before calling `generateSAMLResponseV4`

## Testing with curl

You can also test the POST endpoint directly:

```bash
# First, get a valid SAML request and extract the samlid and assertionUrl from the logs

curl -X POST http://localhost:8080/saml/auth \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "userid=martin" \
  -d "roles=admin,user,marklogic-admin" \
  -d "authn=SUCCESS" \
  -d "samlid=<saml-request-id>" \
  -d "assertionUrl=http://oauth.warnesnet.com:9002"
```

## Expected Complete Log Sequence

```
INFO  c.m.h.undertow.SAMLAuthHandler - Processing SAML authentication for user: martin
INFO  c.m.h.undertow.SAMLAuthHandler - Generating SAML response using OpenSAML 4.x for user: martin
DEBUG c.m.h.undertow.SAMLAuthHandler - Processing roles for user martin: [admin,user,marklogic-admin]
INFO  c.m.h.undertow.SAMLAuthHandler - Added 3 role(s) to SAML assertion: admin,user,marklogic-admin
INFO  c.m.h.undertow.SAMLAuthHandler - Assertion signature configured successfully
INFO  c.m.h.undertow.SAMLAuthHandler - Assertion signed successfully
DEBUG c.m.h.undertow.SAMLAuthHandler - Generated SAML Response XML (length: 2845 chars):
DEBUG c.m.h.undertow.SAMLAuthHandler - <saml2p:Response ...>
DEBUG c.m.h.undertow.SAMLAuthHandler - Generated SAML Response (Base64 decoded, length: 2845 chars):
DEBUG c.m.h.undertow.SAMLAuthHandler - <saml2p:Response ...>
INFO  c.m.h.undertow.SAMLAuthHandler - SAML authentication successful for user: martin with roles: admin,user,marklogic-admin
```

## Checking the HTML Form

Make sure your `authn.html` template includes the roles field:

```html
<form method="post" action="/saml/auth">
    <input type="text" name="userid" placeholder="User ID" />
    <input type="text" name="roles" placeholder="Roles (comma-separated)" />
    <select name="authn">
        <option value="SUCCESS">Success</option>
        <option value="FAILURE">Failure</option>
    </select>
    <input type="hidden" name="samlid" th:value="${saml.samlid}" />
    <input type="hidden" name="assertionUrl" th:value="${saml.assertionUrl}" />
    <input type="hidden" name="notbefore_date" th:value="${saml.notbefore_date}" />
    <input type="hidden" name="notafter_date" th:value="${saml.notafter_date}" />
    <button type="submit">Authenticate</button>
</form>
```
