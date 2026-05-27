package com.group13.auction.service.notification;

import com.group13.auction.service.iservice.INotifier;

public class ConsoleNotifier implements INotifier {
  @Override
  public void notify(String targetId, String title, String message) {
    System.out.printf("[%s - %s] %s%n", title, targetId, message);
  }
}
