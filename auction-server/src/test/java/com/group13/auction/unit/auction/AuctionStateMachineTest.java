package com.group13.auction.unit.auction;

import com.group13.auction.model.auction.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho State Pattern vòng đời phiên đấu giá.
 */
@DisplayName("Auction State Machine")
class AuctionStateMachineTest {

    @Nested
    @DisplayName("OpenState")
    class OpenStateTest {

        @Test
        @DisplayName("start → RUNNING, cancel → CANCELED")
        void validTransitions() {
            AuctionState open = OpenState.INSTANCE;
            assertEquals(Auction.AuctionStatus.OPEN, open.getStatus());
            assertSame(RunningState.INSTANCE, open.start());
            assertSame(CanceledState.INSTANCE, open.cancel());
        }

        @ParameterizedTest
        @MethodSource("illegalFromOpen")
        @DisplayName("close / markPaid từ OPEN → IllegalStateException")
        void illegalTransitions_throw(IllegalTransition action) {
            assertThrows(IllegalStateException.class, action::run);
        }

        static Stream<IllegalTransition> illegalFromOpen() {
            AuctionState open = OpenState.INSTANCE;
            return Stream.of(
                    () -> open.close(true),
                    () -> open.close(false),
                    open::markPaid
            );
        }
    }

    @Nested
    @DisplayName("RunningState")
    class RunningStateTest {

        @Test
        @DisplayName("close(true) → FINISHED, close(false)/cancel → CANCELED")
        void validTransitions() {
            AuctionState running = RunningState.INSTANCE;
            assertEquals(Auction.AuctionStatus.RUNNING, running.getStatus());
            assertSame(FinishedState.INSTANCE, running.close(true));
            assertSame(CanceledState.INSTANCE, running.close(false));
            assertSame(CanceledState.INSTANCE, running.cancel());
        }

        @Test
        @DisplayName("start / markPaid từ RUNNING → IllegalStateException")
        void illegalTransitions_throw() {
            AuctionState running = RunningState.INSTANCE;
            assertThrows(IllegalStateException.class, running::start);
            assertThrows(IllegalStateException.class, running::markPaid);
        }
    }

    @Nested
    @DisplayName("FinishedState")
    class FinishedStateTest {

        @Test
        @DisplayName("markPaid → PAID")
        void markPaid_toPaid() {
            assertSame(PaidState.INSTANCE, FinishedState.INSTANCE.markPaid());
        }

        @Test
        @DisplayName("start / close / cancel → IllegalStateException")
        void illegalTransitions_throw() {
            AuctionState finished = FinishedState.INSTANCE;
            assertThrows(IllegalStateException.class, finished::start);
            assertThrows(IllegalStateException.class, () -> finished.close(true));
            assertThrows(IllegalStateException.class, finished::cancel);
        }
    }

    @Nested
    @DisplayName("Terminal states")
    class TerminalStateTest {

        @Test
        @DisplayName("PAID — mọi transition bị chặn")
        void paid_blocksAll() {
            AuctionState paid = OpenState.INSTANCE.start().close(true).markPaid();
            assertThrows(IllegalStateException.class, paid::start);
            assertThrows(IllegalStateException.class, () -> paid.close(true));
            assertThrows(IllegalStateException.class, paid::cancel);
            assertThrows(IllegalStateException.class, paid::markPaid);
        }

        @Test
        @DisplayName("CANCELED — cancel idempotent, còn lại bị chặn")
        void canceled_cancelIdempotent_otherBlocked() {
            AuctionState canceled = OpenState.INSTANCE.cancel();
            assertDoesNotThrow(canceled::cancel);
            assertThrows(IllegalStateException.class, canceled::start);
            assertThrows(IllegalStateException.class, () -> canceled.close(true));
            assertThrows(IllegalStateException.class, canceled::markPaid);
        }
    }

    @Nested
    @DisplayName("Full lifecycle")
    class LifecycleTest {

        @Test
        @DisplayName("OPEN → RUNNING → FINISHED → PAID")
        void happyPath_toPaid() {
            AuctionState state = OpenState.INSTANCE.start().close(true).markPaid();
            assertEquals(Auction.AuctionStatus.PAID, state.getStatus());
        }

        @Test
        @DisplayName("OPEN → RUNNING → CANCELED (close false hoặc cancel)")
        void paths_toCanceled() {
            assertEquals(Auction.AuctionStatus.CANCELED,
                    OpenState.INSTANCE.start().close(false).getStatus());
            assertEquals(Auction.AuctionStatus.CANCELED,
                    OpenState.INSTANCE.cancel().getStatus());
        }

        @Test
        @DisplayName("transition không mutate state gốc")
        void originalStateUnchanged() {
            AuctionState open = OpenState.INSTANCE;
            AuctionState running = open.start();
            assertEquals(Auction.AuctionStatus.OPEN, open.getStatus());
            assertEquals(Auction.AuctionStatus.RUNNING, running.getStatus());
        }

        @Test
        @DisplayName("close(true) vs close(false) từ RUNNING → state khác nhau")
        void close_hasWinnerFlag_branches() {
            AuctionState running = RunningState.INSTANCE;
            assertEquals(Auction.AuctionStatus.FINISHED, running.close(true).getStatus());
            assertEquals(Auction.AuctionStatus.CANCELED, running.close(false).getStatus());
        }
    }

    @FunctionalInterface
    interface IllegalTransition {
        void run();
    }
}
