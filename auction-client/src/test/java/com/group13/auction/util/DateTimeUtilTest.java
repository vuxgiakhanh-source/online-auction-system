package com.group13.auction.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DateTimeUtil}. */
class DateTimeUtilTest {

  @Test
  void formatDateTimeShouldReturnPlaceholderWhenDateTimeIsNull() {
    assertEquals("--", DateTimeUtil.formatDateTime(null));
  }

  @Test
  void formatDateTimeShouldUseClientDisplayPattern() {
    LocalDateTime dateTime = LocalDateTime.of(2026, 5, 26, 20, 45);

    assertEquals("26/05/2026 20:45", DateTimeUtil.formatDateTime(dateTime));
  }

  @Test
  void formatRemainingShouldReturnFinishedWhenDurationIsNull() {
    assertEquals("Đã kết thúc", DateTimeUtil.formatRemaining(null));
  }

  @Test
  void formatRemainingShouldReturnFinishedWhenDurationIsZero() {
    assertEquals("Đã kết thúc", DateTimeUtil.formatRemaining(Duration.ZERO));
  }

  @Test
  void formatRemainingShouldReturnFinishedWhenDurationIsNegative() {
    assertEquals("Đã kết thúc", DateTimeUtil.formatRemaining(Duration.ofSeconds(-1)));
  }

  @Test
  void formatRemainingShouldDisplayDaysAndHoursWhenDurationHasDays() {
    Duration duration = Duration.ofDays(2).plusHours(3).plusMinutes(15);

    assertEquals("2 ngày 3 giờ", DateTimeUtil.formatRemaining(duration));
  }

  @Test
  void formatRemainingShouldDisplayHoursAndMinutesWhenDurationHasHours() {
    Duration duration = Duration.ofHours(4).plusMinutes(20);

    assertEquals("4 giờ 20 phút", DateTimeUtil.formatRemaining(duration));
  }

  @Test
  void formatRemainingShouldDisplayAtLeastOneMinuteWhenLessThanOneMinute() {
    assertEquals("1 phút", DateTimeUtil.formatRemaining(Duration.ofSeconds(30)));
  }

  @Test
  void formatRemainingShouldDisplayMinutesWhenDurationHasOnlyMinutes() {
    assertEquals("12 phút", DateTimeUtil.formatRemaining(Duration.ofMinutes(12)));
  }
}
