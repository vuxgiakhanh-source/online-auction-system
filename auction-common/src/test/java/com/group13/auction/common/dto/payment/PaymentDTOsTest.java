package com.group13.auction.common.dto.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentDTOs — unit")
class PaymentDTOsTest {

    @Nested @DisplayName("DepositRequestDTO")
    class DepositRequestDTOTest {
        @Test @DisplayName("default constructor — amount zero")
        void defaults() { assertThat(new PaymentDTOs.DepositRequestDTO().getAmount()).isZero(); }
        @Test @DisplayName("1-arg constructor — amount set")
        void oneArgConstructor() {
            assertThat(new PaymentDTOs.DepositRequestDTO(500_000L).getAmount()).isEqualTo(500_000L);
        }
        @Test @DisplayName("setAmount / getAmount — roundtrip")
        void setter() {
            PaymentDTOs.DepositRequestDTO dto = new PaymentDTOs.DepositRequestDTO();
            dto.setAmount(1_000_000L);
            assertThat(dto.getAmount()).isEqualTo(1_000_000L);
        }
    }

    @Nested @DisplayName("WithdrawRequestDTO")
    class WithdrawRequestDTOTest {
        @Test @DisplayName("1-arg constructor — amount set")
        void oneArgConstructor() {
            assertThat(new PaymentDTOs.WithdrawRequestDTO(200_000L).getAmount()).isEqualTo(200_000L);
        }
        @Test @DisplayName("setAmount — roundtrip")
        void setter() {
            PaymentDTOs.WithdrawRequestDTO dto = new PaymentDTOs.WithdrawRequestDTO();
            dto.setAmount(300_000L);
            assertThat(dto.getAmount()).isEqualTo(300_000L);
        }
    }

    @Nested @DisplayName("WalletBalanceResponseDTO")
    class WalletBalanceResponseDTOTest {
        @Test @DisplayName("default constructor — tất cả zero")
        void defaults() {
            PaymentDTOs.WalletBalanceResponseDTO dto = new PaymentDTOs.WalletBalanceResponseDTO();
            assertThat(dto.getBalance()).isZero();
            assertThat(dto.getLockedDeposit()).isZero();
            assertThat(dto.getAvailableBalance()).isZero();
        }
        @Test @DisplayName("3-arg constructor — fields set")
        void threeArgConstructor() {
            PaymentDTOs.WalletBalanceResponseDTO dto =
                    new PaymentDTOs.WalletBalanceResponseDTO(10_000_000L, 500_000L, 9_500_000L);
            assertThat(dto.getBalance()).isEqualTo(10_000_000L);
            assertThat(dto.getLockedDeposit()).isEqualTo(500_000L);
            assertThat(dto.getAvailableBalance()).isEqualTo(9_500_000L);
        }
        @Test @DisplayName("balance = lockedDeposit + availableBalance khi set đúng")
        void balanceInvariant() {
            PaymentDTOs.WalletBalanceResponseDTO dto =
                    new PaymentDTOs.WalletBalanceResponseDTO(10_000_000L, 500_000L, 9_500_000L);
            assertThat(dto.getLockedDeposit() + dto.getAvailableBalance())
                    .isEqualTo(dto.getBalance());
        }
    }

    @Nested @DisplayName("PaymentRequestDTO")
    class PaymentRequestDTOTest {
        @Test @DisplayName("1-arg constructor — auctionId set")
        void oneArgConstructor() {
            assertThat(new PaymentDTOs.PaymentRequestDTO("a-1").getAuctionId()).isEqualTo("a-1");
        }
        @Test @DisplayName("default constructor + setter")
        void setter() {
            PaymentDTOs.PaymentRequestDTO dto = new PaymentDTOs.PaymentRequestDTO();
            dto.setAuctionId("a-2");
            assertThat(dto.getAuctionId()).isEqualTo("a-2");
        }
    }

    @Nested @DisplayName("PaymentResultDTO")
    class PaymentResultDTOTest {
        @Test @DisplayName("defaults — null/zero")
        void defaults() {
            PaymentDTOs.PaymentResultDTO dto = new PaymentDTOs.PaymentResultDTO();
            assertThat(dto.getAuctionId()).isNull();
            assertThat(dto.getFinalPrice()).isZero();
            assertThat(dto.getPaymentStatus()).isNull();
            assertThat(dto.getPaidAt()).isNull();
        }
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            PaymentDTOs.PaymentResultDTO dto = new PaymentDTOs.PaymentResultDTO();
            LocalDateTime ts = LocalDateTime.now();
            dto.setAuctionId("a-1"); dto.setFinalPrice(8_000_000L);
            dto.setDepositDeducted(500_000L); dto.setRemainingToPay(7_500_000L);
            dto.setNewBalance(2_000_000L); dto.setPaymentStatus("COMPLETED");
            dto.setPaidAt(ts);
            assertThat(dto.getFinalPrice()).isEqualTo(8_000_000L);
            assertThat(dto.getDepositDeducted()).isEqualTo(500_000L);
            assertThat(dto.getPaymentStatus()).isEqualTo("COMPLETED");
            assertThat(dto.getPaidAt()).isEqualTo(ts);
        }
        @Test @DisplayName("paymentStatus — COMPLETED / PENDING")
        void paymentStatus_values() {
            for (String s : new String[]{"COMPLETED", "PENDING"}) {
                PaymentDTOs.PaymentResultDTO dto = new PaymentDTOs.PaymentResultDTO();
                dto.setPaymentStatus(s);
                assertThat(dto.getPaymentStatus()).isEqualTo(s);
            }
        }
    }

    @Nested @DisplayName("PaymentExpiredDTO")
    class PaymentExpiredDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            PaymentDTOs.PaymentExpiredDTO dto = new PaymentDTOs.PaymentExpiredDTO();
            dto.setAuctionId("a-1"); dto.setDepositForfeited(500_000L); dto.setRatingPenalty(0.5);
            assertThat(dto.getAuctionId()).isEqualTo("a-1");
            assertThat(dto.getDepositForfeited()).isEqualTo(500_000L);
            assertThat(dto.getRatingPenalty()).isEqualTo(0.5);
        }
    }

    @Nested @DisplayName("DepositRefundDTO")
    class DepositRefundDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            PaymentDTOs.DepositRefundDTO dto = new PaymentDTOs.DepositRefundDTO();
            dto.setAuctionId("a-1"); dto.setRefundAmount(500_000L); dto.setNewBalance(9_500_000L);
            assertThat(dto.getRefundAmount()).isEqualTo(500_000L);
            assertThat(dto.getNewBalance()).isEqualTo(9_500_000L);
        }
    }

    @Nested @DisplayName("DepositForfeitedDTO")
    class DepositForfeitedDTOTest {
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            PaymentDTOs.DepositForfeitedDTO dto = new PaymentDTOs.DepositForfeitedDTO();
            dto.setAuctionId("a-1"); dto.setForfeitedAmount(500_000L); dto.setNewBalance(0L);
            assertThat(dto.getForfeitedAmount()).isEqualTo(500_000L);
            assertThat(dto.getNewBalance()).isZero();
        }
    }

    @Nested @DisplayName("SecondChanceOfferDTO")
    class SecondChanceOfferDTOTest {
        @Test @DisplayName("defaults — null/zero")
        void defaults() {
            PaymentDTOs.SecondChanceOfferDTO dto = new PaymentDTOs.SecondChanceOfferDTO();
            assertThat(dto.getOfferId()).isNull();
            assertThat(dto.getOfferPrice()).isZero();
            assertThat(dto.getDeadline()).isNull();
        }
        @Test @DisplayName("setters / getters — roundtrip")
        void settersGetters() {
            PaymentDTOs.SecondChanceOfferDTO dto = new PaymentDTOs.SecondChanceOfferDTO();
            LocalDateTime deadline = LocalDateTime.now().plusHours(24);
            dto.setOfferId("offer-1"); dto.setAuctionId("a-1");
            dto.setAuctionItemName("Xe máy"); dto.setOfferPrice(6_000_000L);
            dto.setDepositRequired(300_000L); dto.setDeadline(deadline);
            assertThat(dto.getOfferId()).isEqualTo("offer-1");
            assertThat(dto.getOfferPrice()).isEqualTo(6_000_000L);
            assertThat(dto.getDeadline()).isEqualTo(deadline);
        }
    }
}
