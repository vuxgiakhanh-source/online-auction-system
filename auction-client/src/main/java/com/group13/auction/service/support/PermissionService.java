package com.group13.auction.service.support;

import com.group13.auction.core.session.UserSession;

/**
 * Kiểm tra quyền UI theo role/status — mirror logic server {@code AccountService}.
 */
public final class PermissionService {

    private static final PermissionService INSTANCE = new PermissionService();

    private PermissionService() {}

    public static PermissionService getInstance() {
        return INSTANCE;
    }

    public boolean canAccessAdmin(UserSession session) {
        return session != null && session.isAdmin();
    }

    public boolean canManageAuctions(UserSession session) {
        return session != null && session.isSeller();
    }

    public boolean canBid(UserSession session) {
        return session != null && session.isBidder() && isActive(session);
    }

    public boolean isActive(UserSession session) {
        return session != null
                && session.getAccountStatus() != null
                && "ACTIVE".equalsIgnoreCase(session.getAccountStatus());
    }
}
