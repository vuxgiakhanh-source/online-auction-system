import os

files = {
    "src/main/java/com/group13/auction/service/iservice/INotifier.java": """package com.group13.auction.service.iservice;

public interface INotifier {
    void notify(String targetId, String title, String message);
}
""",
    "src/main/java/com/group13/auction/service/notification/ConsoleNotifier.java": """package com.group13.auction.service.notification;

import com.group13.auction.service.iservice.INotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleNotifier implements INotifier {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleNotifier.class);

    @Override
    public void notify(String targetId, String title, String message) {
        logger.info("[{} - {}] {}", title, targetId, message);
    }
}
""",
    "src/main/java/com/group13/auction/service/notification/CompositeNotifier.java": """package com.group13.auction.service.notification;

import com.group13.auction.service.iservice.INotifier;
import java.util.ArrayList;
import java.util.List;

public class CompositeNotifier implements INotifier {
    private final List<INotifier> notifiers = new ArrayList<>();

    public void addNotifier(INotifier notifier) {
        if (notifier != null) {
            this.notifiers.add(notifier);
        }
    }

    public void removeNotifier(INotifier notifier) {
        if (notifier != null) {
            this.notifiers.remove(notifier);
        }
    }

    @Override
    public void notify(String targetId, String title, String message) {
        for (INotifier notifier : notifiers) {
            notifier.notify(targetId, title, message);
        }
    }
}
""",
    "src/main/java/com/group13/auction/service/iservice/IScheduler.java": """package com.group13.auction.service.iservice;

import java.util.concurrent.TimeUnit;

public interface IScheduler {
    void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);
    void shutdownNow();
}
""",
    "src/main/java/com/group13/auction/service/scheduler/TaskScheduler.java": """package com.group13.auction.service.scheduler;

import com.group13.auction.service.iservice.IScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskScheduler implements IScheduler {
    private final ScheduledExecutorService executor;

    public TaskScheduler(int corePoolSize, String threadName) {
        this.executor = Executors.newScheduledThreadPool(corePoolSize, r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        executor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public void shutdownNow() {
        executor.shutdownNow();
    }
}
"""
}

# Fix observer files
for path in ["src/main/java/com/group13/auction/observer/BidderObserver.java", "src/main/java/com/group13/auction/observer/SellerObserver.java", "src/main/java/com/group13/auction/service/AuctionTimerService.java"]:
    try:
        with open(path, "rb") as f:
            content = f.read()
        # strip BOM and null bytes
        content_str = content.replace(b'\x00', b'').decode('utf-8-sig')
        files[path] = content_str
    except Exception as e:
        pass

for path, content in files.items():
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

