package com.tradingplatform.tradingapi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.tradingplatform.domain.orders.Order;
import com.tradingplatform.domain.orders.OrderSide;
import com.tradingplatform.domain.orders.OrderType;
import com.tradingplatform.tradingapi.config.RealmRoleGrantedAuthoritiesConverter;
import com.tradingplatform.tradingapi.orders.OrderApplicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TradingApiSecurityTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private OrderApplicationService orderApplicationService;

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
  void adminPingShouldReturnOkForAdminClientRole() throws Exception {
    mockMvc
        .perform(
            get("/v1/admin/ping")
                .with(
                    jwt()
                        .jwt(
                            jwt ->
                                jwt.claim(
                                    "resource_access",
                                    Map.of("trading-api", Map.of("roles", List.of("ADMIN")))))
                        .authorities(new RealmRoleGrantedAuthoritiesConverter())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.scope").value("admin"));
  }

  @Test
  void publicEndpointsShouldRemainAccessibleWithoutToken() throws Exception {
    mockMvc.perform(get("/v1/version")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs/public")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs/ops")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }

  @Test
  void ordersCreateShouldReturnUnauthorizedWithoutToken() throws Exception {
    mockMvc
        .perform(
            post("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrderRequestJson()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ordersCreateShouldReturnForbiddenForNonTraderRole() throws Exception {
    mockMvc
        .perform(
            post("/v1/orders")
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("VIEWER"))))
                        .authorities(new RealmRoleGrantedAuthoritiesConverter()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrderRequestJson()))
        .andExpect(status().isForbidden());
  }

  @Test
  void ordersCreateShouldReturnAcceptedForTraderRole() throws Exception {
    when(orderApplicationService.createOrder(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            Order.createNew(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.MARKET,
                new BigDecimal("0.1"),
                null,
                "client-1",
                Instant.now()));

    mockMvc
        .perform(
            post("/v1/orders")
                .with(
                    jwt()
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("TRADER"))))
                        .authorities(new RealmRoleGrantedAuthoritiesConverter()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrderRequestJson()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.orderId").exists());
  }

  private static String validOrderRequestJson() {
    return """
        {
          "accountId":"11111111-1111-1111-1111-111111111111",
          "symbol":"BTCUSDT",
          "side":"BUY",
          "type":"MARKET",
          "qty":0.1
        }
        """;
  }
}
