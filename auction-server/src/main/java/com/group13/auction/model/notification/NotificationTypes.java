package com.group13.auction.model.notification;

/** Loại thông báo inbox — map sang {@code NotificationDTO.type} phía client. */
public final class NotificationTypes {

    public static final String SYSTEM = "SYSTEM";
    public static final String SECOND_CHANCE_OFFER = "SecondChanceOffer";

    private NotificationTypes() {}
}
