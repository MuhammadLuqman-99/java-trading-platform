package com.tradingplatform.tradingapi.config;

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
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("scope", "read write")
            .build();

    Set<String> authorities = authorityNames(jwt);
    assertTrue(authorities.contains("SCOPE_read"));
    assertTrue(authorities.contains("SCOPE_write"));
  }

  private Set<String> authorityNames(Jwt jwt) {
    return converter.convert(jwt).stream()
        .map(grantedAuthority -> grantedAuthority.getAuthority())
        .collect(Collectors.toSet());
  }
}
