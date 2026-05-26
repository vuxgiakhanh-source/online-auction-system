package com.group13.auction.network.server.security;

import com.group13.auction.common.messages.RealtimeAccessMessages;
import com.group13.auction.common.protocol.PacketType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Whitelist packet cho tài khoản BANNED/SUSPENDED sau khi đăng nhập (restricted mode).
 * Chỉ ví + đăng xuất + ping; mọi nghiệp vụ khác bị chặn ở {@link RestrictedAccessGuard}.
 */
public final class RestrictedPacketPolicy {

    private static final Set<PacketType> ALLOWED = EnumSet.of(
            PacketType.LOGIN,
            PacketType.LOGOUT,
            PacketType.DEPOSIT,
            PacketType.WITHDRAW,
            PacketType.GET_WALLET_BALANCE,
            PacketType.PING
    );

    private RestrictedPacketPolicy() {}

    public static boolean isAllowed(PacketType type) {
        return type != null && ALLOWED.contains(type);
    }

    public static String denialMessage() {
        return RealtimeAccessMessages.restrictedAccountDenial();
    }
}
