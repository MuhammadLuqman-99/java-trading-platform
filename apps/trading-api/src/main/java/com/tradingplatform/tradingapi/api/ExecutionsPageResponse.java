package com.tradingplatform.tradingapi.api;

import java.util.List;

public record ExecutionsPageResponse(
    List<ExecutionResponse> executions, int page, int size, long totalElements, int totalPages) {}
