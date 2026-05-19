# OAuth 2.0 Token Endpoint Implementation

## Overview

MLEAProxy now includes a simple OAuth 2.0 token endpoint that generates JWT (JSON Web Token) access tokens with custom role claims. This allows applications to obtain access tokens for authentication and authorization purposes.

## Endpoint

**URL**: `POST /oauth/token`  
**Content-Type**: `application/x-www-form-urlencoded`  
**Response**: `application/json`

## Supported Grant Types

### 1. Password Grant (`password`)
Used when the client has the user's credentials (username and password).

### 2. Client Credentials Grant (`client_credentials`)
Used for machine-to-machine authentication where no user is involved.

## Request Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `grant_type` | Yes | Must be `password` or `client_credentials` |
| `client_id` | Yes | Client identifier (can be any string) |
| `client_secret` | Yes | Client secret (can be any string) |
| `username` | Conditional | Required for `password` grant type |
| `password` | Conditional | Required for `password` grant type |
| `scope` | No | OAuth scope (space-separated values) |
| `roles` | No | Comma-separated list of roles to include in the token |

## Response Format

### Success Response (HTTP 200)

```json
{
  "access_token": "eyJraWQiOiI5ZjQ3M...(JWT token)...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

### Error Response (HTTP 4xx)

```json
{
  "error": "invalid_request",
  "error_description": "grant_type is required"
}
```

## JWT Token Structure

The generated JWT access token contains the following claims:

### Standard Claims
- `iss` (Issuer): `MLEAProxy-OAuth-Server`
- `sub` (Subject): Username or client_id
- `aud` (Audience): Client ID
- `iat` (Issued At): Token creation timestamp
- `exp` (Expiration): Token expiration timestamp (default: 1 hour)
- `jti` (JWT ID): Unique token identifier (UUID)

### Custom Claims
- `client_id`: The client identifier
- `grant_type`: The grant type used (`password` or `client_credentials`)
- `username`: User's username (only for password grant)
- `scope`: OAuth scope (if provided)
- `roles`: Array of role strings
- `roles_string`: Space-separated role string (for compatibility)

### Example Decoded Token

```json
{
  "iss": "MLEAProxy-OAuth-Server",
  "sub": "martin",
  "aud": "test-client",
  "iat": 1696348800,
  "exp": 1696352400,
  "jti": "a8f6c3e4-1234-5678-90ab-cdef12345678",
  "client_id": "test-client",
  "grant_type": "password",
  "username": "martin",
  "scope": "read write",
  "roles": ["marklogic-admin", "developer", "user"],
  "roles_string": "marklogic-admin developer user"
}
```

## Token Signing

Tokens are signed using **RS256** (RSA-SHA256) algorithm with the same private key used for SAML assertion signing:
- **Private Key**: `/src/main/resources/static/certificates/privkey.pem` (PKCS8 format)
- **Algorithm**: RSA-SHA256
- **Key Size**: 2048 bits

## Usage Examples

### Example 1: Password Grant with Roles

```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=ml-client" \
  -d "client_secret=ml-secret" \
  -d "username=martin" \
  -d "password=password123" \
  -d "roles=marklogic-admin,developer,user"
```

### Example 2: Client Credentials Grant

```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=service-client" \
  -d "client_secret=service-secret" \
  -d "roles=service,api-access"
```

### Example 3: With Scope

```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=app-client" \
  -d "client_secret=app-secret" \
  -d "username=admin" \
  -d "password=admin123" \
  -d "scope=read write delete" \
  -d "roles=admin"
```

## Error Codes

| Error Code | Description | HTTP Status |
|------------|-------------|-------------|
| `invalid_request` | Missing or malformed required parameter | 400 |
| `invalid_client` | Missing or invalid client credentials | 400 |
| `unsupported_grant_type` | Grant type not supported | 400 |
| `server_error` | Internal server error during token generation | 500 |

## Integration with MarkLogic

### Using Tokens with MarkLogic REST API

Once you have an access token, you can use it with MarkLogic's REST API:

```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=ml-client" \
  -d "client_secret=ml-secret" \
  -d "username=admin" \
  -d "password=admin" \
  -d "roles=admin" | jq -r .access_token)

# Use token with MarkLogic
curl -X GET http://localhost:8000/v1/documents \
  -H "Authorization: Bearer $TOKEN"
```

### Extracting Roles from Tokens

In your MarkLogic application, decode the JWT and extract the roles:

```xquery
xquery version "1.0-ml";

declare function local:decode-jwt($token as xs:string) {
  let $parts := fn:tokenize($token, "\.")
  let $payload := xdmp:base64-decode(fn:replace($parts[2], "_", "/"))
  return xdmp:from-json($payload)
};

let $token := xdmp:get-request-header("Authorization")
let $jwt := fn:substring-after($token, "Bearer ")
let $claims := local:decode-jwt($jwt)
let $roles := $claims/roles
return $roles
```

## Testing

Use the provided REST client file for testing:
```
http_client/oauth.rest
```

This file contains 10 test scenarios including:
- Password grant with single/multiple roles
- Client credentials grant
- Scope handling
- Error scenarios (missing parameters, invalid grant types)

## Token Validation

To validate tokens in your application:

1. **Verify Signature**: Use the public certificate to verify RS256 signature
2. **Check Expiration**: Validate `exp` claim against current time
3. **Validate Issuer**: Ensure `iss` is `MLEAProxy-OAuth-Server`
4. **Check Audience**: Validate `aud` matches expected client_id
5. **Extract Roles**: Use the `roles` array claim for authorization

## Security Considerations

1. **No Authentication**: This is a simplified implementation that accepts any client_id/client_secret combination. In production, validate credentials against a database.

2. **No Scope Validation**: The endpoint accepts any scope value. Implement scope validation based on your requirements.

3. **Role Assignment**: Roles are provided by the client. In production, roles should be looked up from a user directory or database.

4. **Token Expiration**: Default expiration is 1 hour. Adjust `TOKEN_EXPIRATION_SECONDS` constant as needed.

5. **HTTPS**: Always use HTTPS in production to protect credentials and tokens in transit.

## Future Enhancements

Potential improvements for production use:

1. Implement client credential validation against a database
2. Add refresh token support
3. Implement token revocation
4. Add authorization code grant flow
5. Integrate with LDAP/Active Directory for user authentication
6. Add scope-based access control
7. Implement rate limiting
8. Add token introspection endpoint
9. Support for multiple signing keys (key rotation)
10. Add PKCE (Proof Key for Code Exchange) support

## Configuration

No additional configuration is required. The endpoint uses the existing SAML signing certificate:

```properties
# Located at: src/main/resources/static/certificates/privkey.pem
# Certificate validity: October 3, 2025 to October 1, 2035
```

## Logging

The OAuth endpoint logs the following events:

- Token requests (INFO level): client_id, username, roles
- Token generation success (INFO level)
- Errors (WARN/ERROR level): validation errors, exceptions

Example log output:
```
INFO  c.m.h.u.OAuthTokenHandler - OAuth token request - grant_type: password, client_id: test-client, username: martin, scope: read, roles: marklogic-admin,user
DEBUG c.m.h.u.OAuthTokenHandler - Parsed roles: [marklogic-admin, user]
INFO  c.m.h.u.OAuthTokenHandler - OAuth token generated successfully for client: test-client, user: martin, roles: marklogic-admin,user
```

## Troubleshooting

### Problem: "Failed to load private key"
**Solution**: Ensure the private key file exists at `src/main/resources/static/certificates/privkey.pem` and is in PKCS8 format.

### Problem: Token signature verification fails
**Solution**: Ensure you're using the matching public certificate (`certificate.pem`) to verify the signature.

### Problem: Roles not appearing in decoded token
**Solution**: Check that the `roles` parameter is being sent correctly as a comma-separated string.

### Problem: 404 Not Found error
**Solution**: Ensure the application is running and the endpoint is `/oauth/token` (POST method only).

## References

- OAuth 2.0 RFC 6749: https://tools.ietf.org/html/rfc6749
- JWT RFC 7519: https://tools.ietf.org/html/rfc7519
- Nimbus JOSE+JWT Library: https://connect2id.com/products/nimbus-jose-jwt
