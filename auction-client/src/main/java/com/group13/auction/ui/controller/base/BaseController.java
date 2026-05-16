package com.group13.auction.ui.controller.base;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.context.ServiceRegistry;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.state.ScreenStateStore;

/**
 * Controller cơ sở — inject dependency client thống nhất.
 */
public abstract class BaseController {

    protected AppContext appContext() {
        return AppContext.getInstance();
    }

    protected ServiceRegistry services() {
        return appContext().services();
    }

    protected Navigator navigator() {
        return appContext().getNavigator();
    }

    protected SessionManager session() {
        return appContext().getSessionManager();
    }

    protected ScreenStateStore screenState() {
        return appContext().getScreenStateStore();
    }

    protected boolean isLoggedIn() {
        return session().isLoggedIn();
    }
}
