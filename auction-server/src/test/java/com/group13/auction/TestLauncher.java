package com.group13.auction;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.ClassNameFilter.includeClassNamePatterns;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

/**
 * Launcher chạy toàn bộ unit test logic nghiệp vụ OOP.
 *
 * <p>Module này chỉ phụ thuộc các class OOP trong auction-server.
 */
public final class TestLauncher {

    /** Package gốc chứa toàn bộ test logic nghiệp vụ. */
    private static final String TEST_PACKAGE = "com.group13.auction";

    private TestLauncher() {}

    /**
     * Entry point - chạy toàn bộ test và in kết quả ra stdout.
     *
     * @param args không dùng
     */
    public static void main(String[] args) {
        System.out.println("AUCTION SYSTEM — OOP BUSINESS LOGIC TESTS");
        System.out.println();

        LauncherDiscoveryRequest request =
                LauncherDiscoveryRequestBuilder.request()
                        .selectors(selectPackage(TEST_PACKAGE))
                        .filters(includeClassNamePatterns(".*Test"))
                        .build();

        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();

        // Listener tùy chỉnh để in tên test đang chạy
        TestExecutionListener printingListener = new TestExecutionListener() {
            @Override
            public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
                if (testIdentifier.isTest()) {
                    // Chỉ xử lý nếu test không thành công
                    if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                        String status = (result.getStatus() == TestExecutionResult.Status.FAILED) ? "❌ FAILED" : "⚪ SKIPPED";

                        // In tên test và lỗi ra tại đây
                        System.out.println("▶ Running test: " + testIdentifier.getDisplayName());
                        System.out.println("  └ " + status);

                        // Nếu muốn in chi tiết nguyên nhân lỗi (Stacktrace), có thể thêm:
                        // result.getThrowable().ifPresent(t -> t.printStackTrace());
                    }
                }
            }
        };

        Launcher launcher = LauncherFactory.create();
        TestPlan plan = launcher.discover(request);

        System.out.printf("Tìm thấy %d test class(es)%n%n", countTestClasses(plan));

        launcher.registerTestExecutionListeners(printingListener, summaryListener);
        launcher.execute(request);

        TestExecutionSummary summary = summaryListener.getSummary();
        summary.printFailuresTo(new PrintWriter(System.out, true));

        System.out.println();
        System.out.printf("TỔNG KẾT: %d passed / %d failed / %d skipped%n",
                summary.getTestsSucceededCount(),
                summary.getTestsFailedCount(),
                summary.getTestsSkippedCount());
        System.out.printf("Thời gian: %d ms%n",
                summary.getTimeFinished() - summary.getTimeStarted());

        if (summary.getTestsFailedCount() > 0) {
            System.err.println("[FAILED] Có test bị lỗi.");
            System.exit(1);
        } else {
            System.out.println("[PASSED] Tất cả test đều pass.");
        }
    }

    /** Đếm số test class trong plan. */
    private static long countTestClasses(TestPlan plan) {
        return plan.getRoots().stream()
                .flatMap(root -> plan.getChildren(root).stream())
                .flatMap(engine -> plan.getChildren(engine).stream())
                .map(TestIdentifier::getDisplayName)
                .distinct()
                .count();
    }
}