package com.tradingplatform.tradingapi.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public class RealmRoleGrantedAuthoritiesConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {
  private static final String API_CLIENT_ID = "trading-api";

  private final JwtGrantedAuthoritiesConverter scopeConverter =
      new JwtGrantedAuthoritiesConverter();

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();

    Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
    if (scopeAuthorities != null) {
      authorities.addAll(scopeAuthorities);
    }

    addRoleAuthorities(authorities, realmRoles(jwt));
    addRoleAuthorities(authorities, resourceClientRoles(jwt, API_CLIENT_ID));

    return authorities;
  }

  private static Collection<String> realmRoles(Jwt jwt) {
    Object realmAccess = jwt.getClaims().get("realm_access");
    if (realmAccess instanceof Map<?, ?> realmAccessMap) {
      Object roles = realmAccessMap.get("roles");
      if (roles instanceof Collection<?> roleValues) {
        return roleValues.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
      }
    }
    return java.util.List.of();
  }

  private static Collection<String> resourceClientRoles(Jwt jwt, String clientId) {
    Object resourceAccess = jwt.getClaims().get("resource_access");
    if (resourceAccess instanceof Map<?, ?> resourceAccessMap) {
      Object clientAccess = resourceAccessMap.get(clientId);
      if (clientAccess instanceof Map<?, ?> clientAccessMap) {
        Object roles = clientAccessMap.get("roles");
        if (roles instanceof Collection<?> roleValues) {
          return roleValues.stream()
              .filter(String.class::isInstance)
              .map(String.class::cast)
              .toList();
        }
      }
    }
    return java.util.List.of();
  }

  private static void addRoleAuthorities(
      Set<GrantedAuthority> authorities, Collection<String> roleValues) {
    roleValues.stream()
        .filter(role -> !role.isBlank())
        .map(String::toUpperCase)
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .forEach(authorities::add);
  }
}
