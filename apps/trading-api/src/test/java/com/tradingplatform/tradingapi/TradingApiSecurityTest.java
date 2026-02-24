package com.tradingplatform.tradingapi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tradingplatform.tradingapi.config.RealmRoleGrantedAuthoritiesConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TradingApiSecurityTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void adminPingShouldReturnUnauthorizedWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/admin/ping")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminPingShouldReturnForbiddenForNonAdminRole() throws Exception {
    mockMvc
        .perform(
            get("/v1/admin/ping")
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("TRADER"))))
                        .authorities(new RealmRoleGrantedAuthoritiesConverter())))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminPingShouldReturnOkForAdminRole() throws Exception {
    mockMvc
        .perform(
            get("/v1/admin/ping")
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                        .authorities(new RealmRoleGrantedAuthoritiesConverter())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.scope").value("admin"));
  }

  @Test
  void publicEndpointsShouldRemainAccessibleWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/version")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
