package com.group13.auction.service.iservice;

import java.util.concurrent.TimeUnit;

/**
 * Abstraction cho scheduled executor — tách khỏi {@code java.util.concurrent} (DIP).
 */
public interface IScheduler {

    void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    void shutdownNow();
}
