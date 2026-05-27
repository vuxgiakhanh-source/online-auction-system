package com.group13.auction.service.iservice;

@FunctionalInterface
public interface INotifier {
  void notify(String targetId, String title, String message);
}
