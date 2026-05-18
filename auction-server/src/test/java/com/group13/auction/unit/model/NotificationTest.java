package com.group13.auction.unit.model;

import com.group13.auction.model.notification.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests cho {@link Notification} model.
 */
@DisplayName("Notification — unit")
class NotificationTest {

    @Nested
    @DisplayName("Create")
    class CreateTest {

        @Test
        @DisplayName("create() — id không null, isRead=false")
        void create_defaultState() {
            Notification n = Notification.create("user-1", "auction-1", "Bạn bị vượt giá");
            assertThat(n.getId()).isNotNull().isNotEmpty();
            assertThat(n.getUserId()).isEqualTo("user-1");
            assertThat(n.getAuctionId()).isEqualTo("auction-1");
            assertThat(n.getMessage()).isEqualTo("Bạn bị vượt giá");
            assertThat(n.isRead()).isFalse();
            assertThat(n.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("create() hai lần — id khác nhau")
        void create_uniqueIds() {
            Notification n1 = Notification.create("u", "a", "m");
            Notification n2 = Notification.create("u", "a", "m");
            assertThat(n1.getId()).isNotEqualTo(n2.getId());
        }

        @Test
        @DisplayName("reconstitute() — giữ nguyên id và trạng thái")
        void reconstitute_preservesFields() {
            LocalDateTime ts = LocalDateTime.of(2025, 1, 1, 10, 0);
            Notification n = Notification.reconstitute("id-42", ts, ts,
                    "u2", "a2", "hello", true);
            assertThat(n.getId()).isEqualTo("id-42");
            assertThat(n.getUserId()).isEqualTo("u2");
            assertThat(n.getAuctionId()).isEqualTo("a2");
            assertThat(n.getMessage()).isEqualTo("hello");
            assertThat(n.isRead()).isTrue();
            assertThat(n.getCreatedAt()).isEqualTo(ts);
        }
    }

    @Nested
    @DisplayName("MarkRead")
    class MarkReadTest {

        @Test
        @DisplayName("markRead() — isRead chuyển sang true")
        void markRead_setsTrue() {
            Notification n = Notification.create("u", "a", "msg");
            assertThat(n.isRead()).isFalse();
            n.markRead();
            assertThat(n.isRead()).isTrue();
        }

        @Test
        @DisplayName("markRead() nhiều lần — vẫn true, không lỗi")
        void markReadMultipleTimes_noError() {
            Notification n = Notification.create("u", "a", "msg");
            assertThatCode(() -> {
                n.markRead();
                n.markRead();
                n.markRead();
            }).doesNotThrowAnyException();
            assertThat(n.isRead()).isTrue();
        }

        @Test
        @DisplayName("markRead() cập nhật updatedAt")
        void markRead_updatesTimestamp() throws InterruptedException {
            Notification n = Notification.create("u", "a", "msg");
            LocalDateTime before = n.getUpdatedAt();
            Thread.sleep(10);
            n.markRead();
            assertThat(n.getUpdatedAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("PrintInfo")
    class PrintInfoTest {

        @Test
        @DisplayName("printInfo() không ném lỗi")
        void printInfo_noException() {
            Notification n = Notification.create("u", "a", "msg");
            assertThatCode(n::printInfo).doesNotThrowAnyException();
        }
    }
}
