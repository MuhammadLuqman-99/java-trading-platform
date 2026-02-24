package com.tradingplatform.tradingapi.api;

import java.time.Instant;

public record VersionResponse(String application, String version, Instant buildTime) {}
