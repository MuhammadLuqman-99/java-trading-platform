package com.tradingplatform.tradingapi.api;

import java.util.List;

public record OrdersPageResponse(
    List<OrderResponse> orders, int page, int size, long totalElements, int totalPages) {}
