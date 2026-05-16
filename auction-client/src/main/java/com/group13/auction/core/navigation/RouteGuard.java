package com.group13.auction.core.navigation;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.service.support.PermissionService;
import java.util.Optional;

/**
 * Kiểm tra quyền truy cập route trước khi {@link Navigator} chuyển màn.
 */
public final class RouteGuard {

    private final SessionManager sessionManager;
    private final PermissionService permissionService;

    public RouteGuard() {
        this(AppContext.getInstance().getSessionManager(), PermissionService.getInstance());
    }

    RouteGuard(SessionManager sessionManager, PermissionService permissionService) {
        this.sessionManager = sessionManager;
        this.permissionService = permissionService;
    }

    /**
     * @param route route cần mở
     * @return empty nếu được phép; message lỗi nếu bị chặn
     */
    public Optional<String> check(Route route) {
        if (!route.requiresAuth()) {
            return Optional.empty();
        }

        if (!sessionManager.isLoggedIn()) {
            return Optional.of("Vui lòng đăng nhập để tiếp tục.");
        }

        UserSession session = sessionManager.requireSession();

        if (route.requiresAdmin() && !permissionService.canAccessAdmin(session)) {
            return Optional.of("Bạn không có quyền truy cập khu vực quản trị.");
        }

        if (route.requiresSeller() && !permissionService.canManageAuctions(session)) {
            return Optional.of("Chức năng này dành cho tài khoản Seller.");
        }

        return Optional.empty();
    }
}
