import os

path = "src/main/java/com/group13/auction/service/AuctionTimerService.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("import java.util.concurrent.Executors;\nimport java.util.concurrent.ScheduledExecutorService;", "import com.group13.auction.service.iservice.IScheduler;\nimport com.group13.auction.service.scheduler.TaskScheduler;")
content = content.replace("private ScheduledExecutorService scheduler;", "private IScheduler scheduler;")

old_init = """        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auction-timer");
            t.setDaemon(true);
            return t;
        });"""
new_init = """        this.scheduler = new TaskScheduler(1, "auction-timer");"""

content = content.replace(old_init, new_init)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
