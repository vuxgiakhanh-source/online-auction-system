package com.group13.auction.config;

/** Chứa các hằng số giao diện dùng chung cho module client. */
public final class UiConstants {

  public static final String APP_TITLE = "OmniBid - Online Auction System";

  public static final double BASE_WIDTH = 1280.0;
  public static final double BASE_HEIGHT = 720.0;
  public static final double MIN_SCALE = 0.5;
  public static final double MIN_WIDTH = BASE_WIDTH * MIN_SCALE;
  public static final double MIN_HEIGHT = BASE_HEIGHT * MIN_SCALE;
  public static final double DEFAULT_WIDTH = BASE_WIDTH;
  public static final double DEFAULT_HEIGHT = BASE_HEIGHT;

  public static final String DEFAULT_FONT_FAMILY = "Montserrat";
  public static final String SIGNATURE_TEXT = "by CKNT team";

  public static final String ERROR_TITLE = "Có lỗi xảy ra";
  public static final String WARNING_TITLE = "Cảnh báo";
  public static final String INFORMATION_TITLE = "Thông báo";
  public static final String CONFIRMATION_TITLE = "Xác nhận";

  private UiConstants() {
    // Utility class.
  }
}
