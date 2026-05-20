package com.group13.auction.config;

/** Tập trung các đường dẫn tài nguyên tĩnh trong {@code src/main/resources}. */
public final class ResourcePath {

    public static final String APP_CSS = "/com/group13/auction/css/app.css";
    public static final String AUTH_CSS = "/com/group13/auction/css/auth.css";
    public static final String AUCTION_CSS = "/com/group13/auction/css/auction.css";

    public static final String APP_PROPERTIES = "/com/group13/auction/config/app.properties";

    public static final String IMAGE_DIR = "/com/group13/auction/assets/images/";
    public static final String ICON_DIR = "/com/group13/auction/assets/icons/";
    public static final String ICON_ACCENT_DIR = ICON_DIR + "accent/";
    public static final String ICON_NAVY_DIR = ICON_DIR + "navy/";
    public static final String FONT_DIR = "/com/group13/auction/assets/fonts/";
    public static final String MUSIC_DIR = "/com/group13/auction/music/";

    public static final String LOGO_IMAGE = IMAGE_DIR + "logo.png";
    public static final String MASCOT_IMAGE = IMAGE_DIR + "mascot-version-1.png";
    public static final String MASCOT_SECONDARY_IMAGE = IMAGE_DIR + "mascot-version-2.png";
    public static final String BACKGROUND_MUSIC = MUSIC_DIR + "bg.mp3";
    public static final String CLICK_SOUND = MUSIC_DIR + "click.mp3";
    public static final String ERROR_SOUND = MUSIC_DIR + "error.mp3";

    public static final String ICON_AUCTION = ICON_ACCENT_DIR + "icon-auction.png";
    public static final String ICON_BID = ICON_ACCENT_DIR + "icon-bid.png";
    public static final String ICON_CHART = ICON_ACCENT_DIR + "icon-chart-line.png";
    public static final String ICON_GAVEL = ICON_ACCENT_DIR + "icon-gavel.png";
    public static final String ICON_NOTIFICATION = ICON_ACCENT_DIR + "icon-notification.png";
    public static final String ICON_PROFILE = ICON_ACCENT_DIR + "icon-profile.png";
    public static final String ICON_WALLET = ICON_ACCENT_DIR + "icon-wallet.png";

    public static final String FONT_MONTSERRAT_REGULAR = FONT_DIR + "Montserrat-Regular.ttf";
    public static final String FONT_MONTSERRAT_MEDIUM = FONT_DIR + "Montserrat-Medium.ttf";
    public static final String FONT_MONTSERRAT_SEMIBOLD = FONT_DIR + "Montserrat-SemiBold.ttf";
    public static final String FONT_MONTSERRAT_BOLD = FONT_DIR + "Montserrat-Bold.ttf";

    public static final String FONT_BRICOLAGE_REGULAR = FONT_DIR + "BricolageGrotesque-Regular.ttf";
    public static final String FONT_BRICOLAGE_MEDIUM = FONT_DIR + "BricolageGrotesque-Medium.ttf";
    public static final String FONT_BRICOLAGE_SEMIBOLD = FONT_DIR + "BricolageGrotesque-SemiBold.ttf";
    public static final String FONT_BRICOLAGE_BOLD = FONT_DIR + "BricolageGrotesque-Bold.ttf";

    private ResourcePath() {
        // Utility class.
    }
}
