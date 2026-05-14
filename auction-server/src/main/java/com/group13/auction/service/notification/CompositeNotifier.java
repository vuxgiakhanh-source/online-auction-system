package com.group13.auction.service.notification;

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
