package com.tradingplatform.tradingapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RealmRoleGrantedAuthoritiesConverterTest {
  private final RealmRoleGrantedAuthoritiesConverter converter =
      new RealmRoleGrantedAuthoritiesConverter();

  @Test
  void shouldMapRealmRolesToRoleAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("realm_access", Map.of("roles", List.of("ADMIN", "trader")))
            .build();

    Set<String> authorities = authorityNames(jwt);
    assertTrue(authorities.contains("ROLE_ADMIN"));
    assertTrue(authorities.contains("ROLE_TRADER"));
  }

  @Test
  void shouldKeepScopeAuthoritiesWhenScopeClaimExists() {
    Jwt jwt =
        Jwt.withTokenValue("token").header("alg", "none").claim("scope", "read write").build();

    Set<String> authorities = authorityNames(jwt);
    assertTrue(authorities.contains("SCOPE_read"));
    assertTrue(authorities.contains("SCOPE_write"));
  }

  @Test
  void shouldMapTradingApiClientRolesToRoleAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim(
                "resource_access",
                Map.of("trading-api", Map.of("roles", List.of("admin", "compliance"))))
            .build();

    Set<String> authorities = authorityNames(jwt);
    assertTrue(authorities.contains("ROLE_ADMIN"));
    assertTrue(authorities.contains("ROLE_COMPLIANCE"));
  }

  @Test
  void shouldMergeScopeRealmAndClientRolesWithoutDuplicates() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("scope", "read")
            .claim("realm_access", Map.of("roles", List.of("ADMIN", "TRADER")))
            .claim(
                "resource_access",
                Map.of(
                    "trading-api",
                    Map.of("roles", List.of("admin", "COMPLIANCE", "TRADER"))))
            .build();

    Set<String> authorities = authorityNames(jwt);
    assertTrue(authorities.contains("SCOPE_read"));
    assertTrue(authorities.contains("ROLE_ADMIN"));
    assertTrue(authorities.contains("ROLE_TRADER"));
    assertTrue(authorities.contains("ROLE_COMPLIANCE"));
    assertEquals(4, authorities.size());
  }

  @Test
  void shouldIgnoreMalformedResourceAccessClaim() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("resource_access", Map.of("trading-api", "not-a-map"))
            .build();

    Set<String> authorities = authorityNames(jwt);
    assertTrue(authorities.isEmpty());
  }

  private Set<String> authorityNames(Jwt jwt) {
    return converter.convert(jwt).stream()
        .map(grantedAuthority -> grantedAuthority.getAuthority())
        .collect(Collectors.toSet());
  }
}
