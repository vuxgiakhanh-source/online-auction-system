package com.group13.auction.ui.util;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.state.ScreenStateStore;

/**
 * Helper nhỏ cho JavaFX controller để lấy nhanh các dependency dùng chung.
 */
public final class ControllerHelper {

    private ControllerHelper() {
        // Utility class.
    }

    public static AppContext appContext() {
        return AppContext.getInstance();
    }

    public static Navigator navigator() {
        return AppContext.getInstance().getNavigator();
    }

    public static SessionManager sessionManager() {
        return AppContext.getInstance().getSessionManager();
    }

    public static ScreenStateStore screenStateStore() {
        return AppContext.getInstance().getScreenStateStore();
    }
}