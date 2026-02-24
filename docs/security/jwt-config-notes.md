# JWT Configuration Notes (Keycloak)

This document defines local JWT conventions for the `trading-platform` realm.

## Realm and Roles

- Realm: `trading-platform`
- Realm roles: `TRADER`, `VIEWER`, `ADMIN`, `COMPLIANCE`
- Role claim sources:
  - `realm_access.roles`
  - `resource_access.trading-api.roles`

## Keycloak Clients (MVP)

- `trading-api` (confidential):
  - Service accounts enabled
  - Intended audience for API token validation
- `trading-api-swagger` (public):
  - Authorization Code with PKCE (`S256`)
  - Direct Access Grants enabled for local curl/Postman password-flow checks only
  - Local redirect URI coverage for Swagger and Postman callbacks

## Local Issuer Values

Use one issuer value depending on where the Spring app runs:

- App runs on host machine:
  - `http://localhost:8080/realms/trading-platform`
- App runs in Docker network:
  - `http://keycloak:8080/realms/trading-platform`

## Realm Import Behavior

- Compose starts Keycloak with `--import-realm` and reads files from `/opt/keycloak/data/import`.
- Realm import is bootstrap-oriented; existing realm objects are not automatically overwritten.
- After changing `realm-trading-platform-dev.json`, re-import by removing the realm in Keycloak UI or resetting local Keycloak/Postgres data.

## Spring Boot Resource Server Properties

Host-run example:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:http://localhost:8080/realms/trading-platform}
```

Container-run example:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:http://keycloak:8080/realms/trading-platform}
```

Fallback when OIDC discovery is blocked:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWK_SET_URI:http://localhost:8080/realms/trading-platform/protocol/openid-connect/certs}
```

## Role Mapping to Spring Authorities

Map both `realm_access.roles` and `resource_access.trading-api.roles` into Spring
authorities as `ROLE_*`:

- `TRADER -> ROLE_TRADER`
- `VIEWER -> ROLE_VIEWER`
- `ADMIN -> ROLE_ADMIN`
- `COMPLIANCE -> ROLE_COMPLIANCE`

Then protect endpoints with normal role checks, for example:

- `hasRole('TRADER')` for trading endpoints
- `hasAnyRole('ADMIN','COMPLIANCE')` for admin/audit endpoints

## Audience Guidance

MVP default:

- Validate issuer and signature first.
- Keep audience checks optional until all clients consistently include `aud=trading-api`.

When enabling strict audience validation:

- Require `aud` to contain `trading-api`.
- Keep the audience mapper on `trading-api-swagger` so browser/Postman tokens are accepted by API services.

## Quick Local Token Check

Token endpoint:

`http://localhost:8080/realms/trading-platform/protocol/openid-connect/token`

Example request using seeded dev user:

```bash
curl -X POST "http://localhost:8080/realms/trading-platform/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=trading-api-swagger" \
  -d "username=trader_dev" \
  -d "password=ChangeMe123!"
```

Use the returned `access_token` as `Authorization: Bearer <token>` when calling protected endpoints.
