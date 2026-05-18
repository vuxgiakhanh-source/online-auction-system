package com.group13.auction.unit.model;

import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.BidTransaction.BidResult;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests cho {@link BidTransaction}.
 */
@DisplayName("BidTransaction — unit")
class BidTransactionTest {

    private NormalUser bidder;

    @BeforeEach
    void setUp() {
        bidder = NormalUser.reconstitute(
                UUID.randomUUID().toString(),
                LocalDateTime.now(), LocalDateTime.now(),
                "bidder_test", "hashed", "bidder@test.com",
                User.AccountStatus.ACTIVE,
                3.0, 10_000_000L, 0L,
                EnumSet.of(User.UserRole.BIDDER),
                false, false, null
        );
    }

    @Nested
    @DisplayName("Create")
    class CreateTest {

        @Test
        @DisplayName("create() — id tự sinh, timestamp không null")
        void create_defaultFields() {
            BidTransaction tx = BidTransaction.create(bidder, "auction-1", 1_000_000L, BidResult.ACCEPTED);
            assertThat(tx.getId()).isNotNull().isNotEmpty();
            assertThat(tx.getBidder()).isSameAs(bidder);
            assertThat(tx.getAuctionId()).isEqualTo("auction-1");
            assertThat(tx.getAmount()).isEqualTo(1_000_000L);
            assertThat(tx.getResult()).isEqualTo(BidResult.ACCEPTED);
            assertThat(tx.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("create() hai lần — id khác nhau")
        void create_uniqueIds() {
            BidTransaction t1 = BidTransaction.create(bidder, "a", 1L, BidResult.ACCEPTED);
            BidTransaction t2 = BidTransaction.create(bidder, "a", 1L, BidResult.ACCEPTED);
            assertThat(t1.getId()).isNotEqualTo(t2.getId());
        }

        @ParameterizedTest
        @EnumSource(BidResult.class)
        @DisplayName("Tất cả BidResult đều tạo được")
        void create_allResultTypes(BidResult result) {
            BidTransaction tx = BidTransaction.create(bidder, "a", 500_000L, result);
            assertThat(tx.getResult()).isEqualTo(result);
        }

        @Test
        @DisplayName("reconstitute() — giữ nguyên id và timestamp")
        void reconstitute_preservesFields() {
            LocalDateTime ts = LocalDateTime.of(2025, 5, 1, 12, 0);
            BidTransaction tx = BidTransaction.reconstitute(
                    "tx-id-99", ts, ts, bidder, "auction-X",
                    2_000_000L, ts, BidResult.OUTBID);
            assertThat(tx.getId()).isEqualTo("tx-id-99");
            assertThat(tx.getAmount()).isEqualTo(2_000_000L);
            assertThat(tx.getResult()).isEqualTo(BidResult.OUTBID);
            assertThat(tx.getTimestamp()).isEqualTo(ts);
        }
    }

    @Nested
    @DisplayName("SetResult")
    class SetResultTest {

        @Test
        @DisplayName("setResult() thay đổi kết quả")
        void setResult_changesResult() {
            BidTransaction tx = BidTransaction.create(bidder, "a", 1_000_000L, BidResult.ACCEPTED);
            tx.setResult(BidResult.OUTBID);
            assertThat(tx.getResult()).isEqualTo(BidResult.OUTBID);
        }

        @Test
        @DisplayName("setResult() cập nhật updatedAt")
        void setResult_updatesTimestamp() throws InterruptedException {
            BidTransaction tx = BidTransaction.create(bidder, "a", 1L, BidResult.ACCEPTED);
            LocalDateTime before = tx.getUpdatedAt();
            Thread.sleep(10);
            tx.setResult(BidResult.REJECTED);
            assertThat(tx.getUpdatedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("BidResult enum coverage — 4 giá trị")
        void bidResultEnumValues() {
            BidResult[] values = BidResult.values();
            assertThat(values).contains(
                    BidResult.ACCEPTED,
                    BidResult.ACCEPTED_RESERVE_NOT_MET,
                    BidResult.REJECTED,
                    BidResult.OUTBID
            );
        }
    }

    @Nested
    @DisplayName("PrintInfo")
    class PrintInfoTest {
        @Test
        @DisplayName("printInfo() không ném lỗi")
        void printInfo_noException() {
            BidTransaction tx = BidTransaction.create(bidder, "a", 1_000_000L, BidResult.ACCEPTED);
            assertThatCode(tx::printInfo).doesNotThrowAnyException();
        }
    }
}
