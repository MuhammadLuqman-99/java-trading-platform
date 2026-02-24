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
  private final JwtGrantedAuthoritiesConverter scopeConverter =
      new JwtGrantedAuthoritiesConverter();

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();

    Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
    if (scopeAuthorities != null) {
      authorities.addAll(scopeAuthorities);
    }

    Object realmAccess = jwt.getClaims().get("realm_access");
    if (realmAccess instanceof Map<?, ?> realmAccessMap) {
      Object roles = realmAccessMap.get("roles");
      if (roles instanceof Collection<?> roleValues) {
        roleValues.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(role -> !role.isBlank())
            .map(role -> role.toUpperCase())
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);
      }
    }

    return authorities;
  }
}
