package com.group13.auction.service.support;

/**
 * Compatibility facade — giữ lại để không break call site cũ.
 *
 * <p><b>Deprecated:</b> tất cả code mới phải gọi {@link AudioManager} trực tiếp. Class này sẽ bị
 * xóa trong phiên bản tiếp theo.
 *
 * <p>Lý do: SoundManager chỉ là wrapper 1-1 sang AudioManager mà không thêm logic nào. Duy trì 2
 * lớp cho cùng chức năng làm tăng confusion khi đọc code.
 */
@Deprecated(since = "v9", forRemoval = true)
public final class SoundManager {

  private SoundManager() {}

  /**
   * @deprecated Dùng {@link AudioManager#playClickSound()}
   */
  @Deprecated(since = "v9", forRemoval = true)
  public static void playClickSound() {
    AudioManager.playClickSound();
  }

  /**
   * @deprecated Dùng {@link AudioManager#playErrorSound()}
   */
  @Deprecated(since = "v9", forRemoval = true)
  public static void playErrorSound() {
    AudioManager.playErrorSound();
  }

  /**
   * @deprecated Dùng {@link AudioManager#shutdown()}
   */
  @Deprecated(since = "v9", forRemoval = true)
  public static void shutdown() {
    AudioManager.shutdown();
  }
}
