package com.group13.auction.integration.base;

import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.PrintWriter;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Launcher chạy toàn bộ Integration Tests.
 *
 * <p>Cấu trúc test (cùng cấp dưới {@code com.group13.auction}): unit/ — *Test.java (Surefire,
 * {@link com.group13.auction.unit.TestLauncher}) integration/ — *IT.java, *IntegrationTest.java
 * (Failsafe + Docker) concurrency/ — BidConcurrencyTest (mock, Surefire) load/ — BidServiceLoadIT
 * (Docker), … websocket/ — BidWebSocketIntegrationIT (Docker), …
 *
 * <p>Package concret: integration.base/ — IntegrationTestBase, Launcher, RequiresDocker
 * integration.dao/, service.*, user/, notification/ websocket/ — *IT WebSocket × DB
 * (Testcontainers)
 */
public final class IntegrationTestLauncher {

  private static final String ROOT = "com.group13.auction.integration";
  private static final String WEBSOCKET_IT = "com.group13.auction.websocket";

  private IntegrationTestLauncher() {}

  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════╗");
    System.out.println("║      AUCTION SYSTEM — INTEGRATION TESTS                  ║");
    System.out.println("║  Yêu cầu: Docker daemon đang chạy (Testcontainers)       ║");
    System.out.println("╚══════════════════════════════════════════════════════════╝\n");

    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(
                selectPackage(ROOT),
                selectPackage(WEBSOCKET_IT),
                selectPackage("com.group13.auction.load"))
            .filters(includeClassNamePatterns(".*IT", ".*IntegrationTest"))
            .build();

    SummaryGeneratingListener summary = new SummaryGeneratingListener();
    TestExecutionListener printer =
        new TestExecutionListener() {
          @Override
          public void executionStarted(TestIdentifier id) {
            if (id.isTest()) {
              System.out.println("  ▶ " + id.getDisplayName());
            }
          }

          @Override
          public void executionFinished(TestIdentifier id, TestExecutionResult result) {
            if (!id.isTest()) {
              if (!id.getParentId().isEmpty())
                System.out.println("\n[ " + id.getDisplayName() + " ]");
              return;
            }
            switch (result.getStatus()) {
              case SUCCESSFUL -> System.out.println("    ✅ PASSED");
              case FAILED -> {
                System.out.println("    ❌ FAILED");
                result
                    .getThrowable()
                    .ifPresent(t -> System.out.println("       " + t.getMessage()));
              }
              case ABORTED -> System.out.println("    ⚪ SKIPPED");
            }
          }
        };

    Launcher launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(printer, summary);
    launcher.execute(request);

    TestExecutionSummary s = summary.getSummary();
    System.out.println("\n══════════════════════════════════════════════════════════");
    if (s.getTestsFailedCount() == 0) {
      System.out.println("  FAILURES: (không có)");
    } else s.printFailuresTo(new PrintWriter(System.out, true));
    System.out.printf(
        "%n  TỔNG KẾT : %d passed  /  %d failed  /  %d skipped%n",
        s.getTestsSucceededCount(), s.getTestsFailedCount(), s.getTestsSkippedCount());
    System.out.printf("  Thời gian: %d ms%n", s.getTimeFinished() - s.getTimeStarted());
    System.out.println("══════════════════════════════════════════════════════════");

    if (s.getTestsFailedCount() > 0) {
      System.err.println("[FAILED] " + s.getTestsFailedCount() + " test bị lỗi.");
      System.exit(1);
    } else {
      System.out.println("[PASSED] Tất cả " + s.getTestsSucceededCount() + " test đều pass.");
    }
  }
}
