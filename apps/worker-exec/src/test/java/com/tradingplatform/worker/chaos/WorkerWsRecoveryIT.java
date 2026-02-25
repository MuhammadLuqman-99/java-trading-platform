package com.tradingplatform.worker.chaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RUN_WS_CHAOS_TEST", matches = "(?i)(true|1|yes)")
class WorkerWsRecoveryIT {
  private static final Duration CHAOS_TIMEOUT = Duration.ofMinutes(8);

  @Test
  void shouldRecoverWorkerWsConnectivityAfterContainerKill() throws Exception {
    Path projectRoot = Path.of("..", "..").toAbsolutePath().normalize();
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    boolean windows = osName.contains("win");
    Path script =
        windows
            ? projectRoot.resolve("scripts").resolve("chaos").resolve("ws_worker_recover.ps1")
            : projectRoot.resolve("scripts").resolve("chaos").resolve("ws_worker_recover.sh");

    List<String> command = new ArrayList<>();
    if (windows) {
      command.add("powershell");
      command.add("-NoProfile");
      command.add("-ExecutionPolicy");
      command.add("Bypass");
      command.add("-File");
      command.add(script.toString());
    } else {
      command.add("bash");
      command.add(script.toString());
    }

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(projectRoot.toFile());
    processBuilder.redirectErrorStream(true);
    Process process = processBuilder.start();

    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append(System.lineSeparator());
      }
    }

    boolean completed = process.waitFor(CHAOS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    if (!completed) {
      process.destroyForcibly();
      throw new IllegalStateException("Chaos test command timed out");
    }

    int exitCode = process.exitValue();
    assertEquals(0, exitCode, "Chaos test failed:\n" + output);
    assertTrue(output.toString().contains("RECOVERY_OK"), "Missing recovery marker:\n" + output);
  }
}
