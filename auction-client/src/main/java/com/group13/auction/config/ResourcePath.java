package com.group13.auction.config;

/**
 * Tập trung các đường dẫn tài nguyên tĩnh trong {@code src/main/resources}.
 */
public final class ResourcePath {

    public static final String APP_CSS = "/com/group13/auction/css/app.css";
    public static final String AUTH_CSS = "/com/group13/auction/css/auth.css";
    public static final String AUCTION_CSS = "/com/group13/auction/css/auction.css";

    public static final String APP_PROPERTIES = "/com/group13/auction/config/app.properties";

    public static final String IMAGE_DIR = "/com/group13/auction/assets/images/";
    public static final String ICON_DIR = "/com/group13/auction/assets/icons/";
    public static final String FONT_DIR = "/com/group13/auction/assets/fonts/";

    private ResourcePath() {
        // Utility class.
    }
}