package com.group13.auction.service.scheduler;

import com.group13.auction.service.iservice.IScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskScheduler implements IScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final ScheduledExecutorService executor;

    public TaskScheduler(int corePoolSize, String threadName) {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be positive");
        }
        if (threadName == null || threadName.isBlank()) {
            throw new IllegalArgumentException("threadName must not be blank");
        }
        this.executor = Executors.newScheduledThreadPool(corePoolSize, r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        executor.scheduleAtFixedRate(() -> {
            try {
                command.run();
            } catch (Throwable t) {
                log.error("Scheduled task failed", t);
            }
        }, initialDelay, period, unit);
    }

    @Override
    public void shutdownNow() {
        executor.shutdownNow();
    }
}
