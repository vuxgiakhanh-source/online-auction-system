package com.group13.auction.common.messages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RealtimeAccessMessages")
class RealtimeAccessMessagesTest {

  @Test
  @DisplayName("restrictedAccountDenial() trả về tiếng Việt, không chứa tiếng Anh cũ")
  void restrictedAccountDenial_isVietnamese() {
    String message = RealtimeAccessMessages.restrictedAccountDenial();

    assertThat(message).contains("Tài khoản");
    assertThat(message).contains("ví");
    assertThat(message).doesNotContain("Access denied");
    assertThat(message).doesNotContain("Account is suspended");
  }

  @Test
  @DisplayName("bannedFromAuctionFeature() trả về tiếng Việt")
  void bannedFromAuctionFeature_isVietnamese() {
    String message = RealtimeAccessMessages.bannedFromAuctionFeature();

    assertThat(message).contains("Bạn");
    assertThat(message).contains("cấm");
  }
}
