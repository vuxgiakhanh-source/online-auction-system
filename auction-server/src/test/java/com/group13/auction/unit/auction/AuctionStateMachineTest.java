package com.group13.auction.unit.auction;

import com.group13.auction.model.auction.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho State Pattern của vòng đời phiên đấu giá.
 *
 * <p>Kiểm tra contract của từng state:
 * <ul>
 *   <li>Transition hợp lệ trả về đúng state mới</li>
 *   <li>Transition không hợp lệ ném {@link IllegalStateException}</li>
 *   <li>{@code getStatus()} trả về đúng enum tương ứng</li>
 *   <li>State trả về đúng singleton instance (identity check)</li>
 * </ul>
 *
 * <p>Không cần mock — tất cả state là Singleton immutable, zero dependencies.
 */
@DisplayName("Auction State Machine")
class AuctionStateMachineTest {

    // =========================================================================
    // OpenState
    // =========================================================================

    @Nested
    @DisplayName("OpenState")
    class OpenStateTest {

        // -- getStatus --------------------------------------------------------

        @Test
        @DisplayName("getStatus trả về OPEN")
        void getStatus_returnsOpen() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            Auction.AuctionStatus status = state.getStatus();

            // Assert
            assertEquals(Auction.AuctionStatus.OPEN, status);
        }

        // -- Valid transitions ------------------------------------------------

        @Test
        @DisplayName("start() chuyển OPEN → RUNNING")
        void start_fromOpen_returnsRunningState() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            AuctionState next = state.start();

            // Assert
            assertSame(RunningState.INSTANCE, next);
        }

        @Test
        @DisplayName("start() trả về đúng singleton RunningState")
        void start_fromOpen_returnsSameRunningInstance() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            AuctionState next = state.start();

            // Assert
            assertEquals(Auction.AuctionStatus.RUNNING, next.getStatus());
        }

        @Test
        @DisplayName("cancel() chuyển OPEN → CANCELED")
        void cancel_fromOpen_returnsCanceledState() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            AuctionState next = state.cancel();

            // Assert
            assertSame(CanceledState.INSTANCE, next);
        }

        @Test
        @DisplayName("cancel() trả về đúng singleton CanceledState")
        void cancel_fromOpen_returnsSameCanceledInstance() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            AuctionState next = state.cancel();

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, next.getStatus());
        }

        // -- Invalid transitions ----------------------------------------------

        @Test
        @DisplayName("close(true) từ OPEN ném IllegalStateException")
        void close_withWinner_fromOpen_throwsIllegalState() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(true));
        }

        @Test
        @DisplayName("close(false) từ OPEN ném IllegalStateException")
        void close_withoutWinner_fromOpen_throwsIllegalState() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(false));
        }

        @Test
        @DisplayName("markPaid() từ OPEN ném IllegalStateException")
        void markPaid_fromOpen_throwsIllegalState() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::markPaid);
        }

        @Test
        @DisplayName("close(true) exception chứa thông tin trạng thái hiện tại")
        void close_fromOpen_exceptionMessageContainsOpenState() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, () -> state.close(true));

            // Assert
            assertTrue(ex.getMessage().contains("OPEN"));
        }

        @Test
        @DisplayName("markPaid() exception chứa thông tin trạng thái hiện tại")
        void markPaid_fromOpen_exceptionMessageContainsOpenState() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::markPaid);

            // Assert
            assertTrue(ex.getMessage().contains("OPEN"));
        }

        // -- Singleton identity -----------------------------------------------

        @Test
        @DisplayName("INSTANCE là singleton — cùng một tham chiếu")
        void instance_isSingleton() {
            // Assert
            assertSame(OpenState.INSTANCE, OpenState.INSTANCE);
        }
    }

    // =========================================================================
    // RunningState
    // =========================================================================

    @Nested
    @DisplayName("RunningState")
    class RunningStateTest {

        // -- getStatus --------------------------------------------------------

        @Test
        @DisplayName("getStatus trả về RUNNING")
        void getStatus_returnsRunning() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            Auction.AuctionStatus status = state.getStatus();

            // Assert
            assertEquals(Auction.AuctionStatus.RUNNING, status);
        }

        // -- Valid transitions ------------------------------------------------

        @Test
        @DisplayName("close(true) từ RUNNING → FINISHED")
        void close_withWinner_fromRunning_returnsFinishedState() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            AuctionState next = state.close(true);

            // Assert
            assertSame(FinishedState.INSTANCE, next);
        }

        @Test
        @DisplayName("close(true) trả về status FINISHED")
        void close_withWinner_fromRunning_nextStatusIsFinished() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            AuctionState next = state.close(true);

            // Assert
            assertEquals(Auction.AuctionStatus.FINISHED, next.getStatus());
        }

        @Test
        @DisplayName("close(false) từ RUNNING → CANCELED (không có winner)")
        void close_withoutWinner_fromRunning_returnsCanceledState() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            AuctionState next = state.close(false);

            // Assert
            assertSame(CanceledState.INSTANCE, next);
        }

        @Test
        @DisplayName("close(false) trả về status CANCELED")
        void close_withoutWinner_fromRunning_nextStatusIsCanceled() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            AuctionState next = state.close(false);

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, next.getStatus());
        }

        @Test
        @DisplayName("cancel() từ RUNNING → CANCELED")
        void cancel_fromRunning_returnsCanceledState() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            AuctionState next = state.cancel();

            // Assert
            assertSame(CanceledState.INSTANCE, next);
        }

        @Test
        @DisplayName("cancel() và close(false) từ RUNNING đều cho cùng kết quả CANCELED")
        void cancel_and_closeWithoutWinner_fromRunning_returnSameInstance() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            AuctionState viaCancelMethod = state.cancel();
            AuctionState viaCloseMethod = state.close(false);

            // Assert
            assertSame(viaCancelMethod, viaCloseMethod);
        }

        // -- Invalid transitions ----------------------------------------------

        @Test
        @DisplayName("start() từ RUNNING ném IllegalStateException")
        void start_fromRunning_throwsIllegalState() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::start);
        }

        @Test
        @DisplayName("markPaid() từ RUNNING ném IllegalStateException")
        void markPaid_fromRunning_throwsIllegalState() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::markPaid);
        }

        @Test
        @DisplayName("start() exception chứa thông tin trạng thái hiện tại")
        void start_fromRunning_exceptionMessageContainsRunningState() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::start);

            // Assert
            assertTrue(ex.getMessage().contains("RUNNING"));
        }

        @Test
        @DisplayName("markPaid() exception chứa thông tin trạng thái hiện tại")
        void markPaid_fromRunning_exceptionMessageContainsRunningState() {
            // Arrange
            AuctionState state = RunningState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::markPaid);

            // Assert
            assertTrue(ex.getMessage().contains("RUNNING"));
        }

        // -- Singleton identity -----------------------------------------------

        @Test
        @DisplayName("INSTANCE là singleton — cùng một tham chiếu")
        void instance_isSingleton() {
            // Assert
            assertSame(RunningState.INSTANCE, RunningState.INSTANCE);
        }
    }

    // =========================================================================
    // FinishedState
    // =========================================================================

    @Nested
    @DisplayName("FinishedState")
    class FinishedStateTest {

        // -- getStatus --------------------------------------------------------

        @Test
        @DisplayName("getStatus trả về FINISHED")
        void getStatus_returnsFinished() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act
            Auction.AuctionStatus status = state.getStatus();

            // Assert
            assertEquals(Auction.AuctionStatus.FINISHED, status);
        }

        // -- Valid transitions ------------------------------------------------

        @Test
        @DisplayName("markPaid() từ FINISHED → PAID")
        void markPaid_fromFinished_returnsPaidState() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act
            AuctionState next = state.markPaid();

            // Assert
            assertSame(PaidState.INSTANCE, next);
        }

        @Test
        @DisplayName("markPaid() trả về status PAID")
        void markPaid_fromFinished_nextStatusIsPaid() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act
            AuctionState next = state.markPaid();

            // Assert
            assertEquals(Auction.AuctionStatus.PAID, next.getStatus());
        }

        // -- Invalid transitions ----------------------------------------------

        @Test
        @DisplayName("start() từ FINISHED ném IllegalStateException")
        void start_fromFinished_throwsIllegalState() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::start);
        }

        @Test
        @DisplayName("close(true) từ FINISHED ném IllegalStateException")
        void close_withWinner_fromFinished_throwsIllegalState() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(true));
        }

        @Test
        @DisplayName("close(false) từ FINISHED ném IllegalStateException")
        void close_withoutWinner_fromFinished_throwsIllegalState() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(false));
        }

        @Test
        @DisplayName("cancel() từ FINISHED ném IllegalStateException")
        void cancel_fromFinished_throwsIllegalState() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::cancel);
        }

        @Test
        @DisplayName("start() exception chứa thông tin 'FINISHED'")
        void start_fromFinished_exceptionMessageContainsFinished() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::start);

            // Assert
            assertTrue(ex.getMessage().contains("FINISHED"));
        }

        @Test
        @DisplayName("cancel() exception chứa thông tin 'FINISHED'")
        void cancel_fromFinished_exceptionMessageContainsFinished() {
            // Arrange
            AuctionState state = FinishedState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::cancel);

            // Assert
            assertTrue(ex.getMessage().contains("FINISHED"));
        }

        // -- Singleton identity -----------------------------------------------

        @Test
        @DisplayName("INSTANCE là singleton — cùng một tham chiếu")
        void instance_isSingleton() {
            // Assert
            assertSame(FinishedState.INSTANCE, FinishedState.INSTANCE);
        }
    }

    // =========================================================================
    // PaidState
    // =========================================================================

    @Nested
    @DisplayName("PaidState — terminal state, mọi transition đều bị chặn")
    class PaidStateTest {

        // -- getStatus --------------------------------------------------------

        @Test
        @DisplayName("getStatus trả về PAID")
        void getStatus_returnsPaid() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act
            Auction.AuctionStatus status = state.getStatus();

            // Assert
            assertEquals(Auction.AuctionStatus.PAID, status);
        }

        // -- Invalid transitions (terminal state) -----------------------------

        @Test
        @DisplayName("start() từ PAID ném IllegalStateException")
        void start_fromPaid_throwsIllegalState() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::start);
        }

        @Test
        @DisplayName("close(true) từ PAID ném IllegalStateException")
        void close_withWinner_fromPaid_throwsIllegalState() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(true));
        }

        @Test
        @DisplayName("close(false) từ PAID ném IllegalStateException")
        void close_withoutWinner_fromPaid_throwsIllegalState() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(false));
        }

        @Test
        @DisplayName("cancel() từ PAID ném IllegalStateException")
        void cancel_fromPaid_throwsIllegalState() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::cancel);
        }

        @Test
        @DisplayName("markPaid() từ PAID ném IllegalStateException (không thể đánh dấu lại)")
        void markPaid_fromPaid_throwsIllegalState() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::markPaid);
        }

        @Test
        @DisplayName("start() exception chứa thông tin 'PAID'")
        void start_fromPaid_exceptionMessageContainsPaid() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::start);

            // Assert
            assertTrue(ex.getMessage().contains("PAID"));
        }

        @Test
        @DisplayName("cancel() exception chứa thông tin 'PAID'")
        void cancel_fromPaid_exceptionMessageContainsPaid() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::cancel);

            // Assert
            assertTrue(ex.getMessage().contains("PAID"));
        }

        @Test
        @DisplayName("markPaid() exception chứa thông tin 'PAID'")
        void markPaid_fromPaid_exceptionMessageContainsPaid() {
            // Arrange
            AuctionState state = PaidState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::markPaid);

            // Assert
            assertTrue(ex.getMessage().contains("PAID"));
        }

        // -- Singleton identity -----------------------------------------------

        @Test
        @DisplayName("INSTANCE là singleton — cùng một tham chiếu")
        void instance_isSingleton() {
            // Assert
            assertSame(PaidState.INSTANCE, PaidState.INSTANCE);
        }
    }

    // =========================================================================
    // CanceledState
    // =========================================================================

    @Nested
    @DisplayName("CanceledState — terminal state, cancel() idempotent")
    class CanceledStateTest {

        // -- getStatus --------------------------------------------------------

        @Test
        @DisplayName("getStatus trả về CANCELED")
        void getStatus_returnsCanceled() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act
            Auction.AuctionStatus status = state.getStatus();

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, status);
        }

        // -- Idempotent cancel ------------------------------------------------

        @Test
        @DisplayName("cancel() từ CANCELED trả về chính nó (idempotent)")
        void cancel_fromCanceled_returnsSameInstance() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act
            AuctionState next = state.cancel();

            // Assert
            assertSame(CanceledState.INSTANCE, next);
        }

        @Test
        @DisplayName("cancel() gọi nhiều lần vẫn giữ nguyên CANCELED")
        void cancel_calledMultipleTimes_alwaysReturnsCanceled() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act
            AuctionState first  = state.cancel();
            AuctionState second = first.cancel();
            AuctionState third  = second.cancel();

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, first.getStatus());
            assertEquals(Auction.AuctionStatus.CANCELED, second.getStatus());
            assertEquals(Auction.AuctionStatus.CANCELED, third.getStatus());
        }

        // -- Invalid transitions (terminal state) -----------------------------

        @Test
        @DisplayName("start() từ CANCELED ném IllegalStateException")
        void start_fromCanceled_throwsIllegalState() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::start);
        }

        @Test
        @DisplayName("close(true) từ CANCELED ném IllegalStateException")
        void close_withWinner_fromCanceled_throwsIllegalState() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(true));
        }

        @Test
        @DisplayName("close(false) từ CANCELED ném IllegalStateException")
        void close_withoutWinner_fromCanceled_throwsIllegalState() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, () -> state.close(false));
        }

        @Test
        @DisplayName("markPaid() từ CANCELED ném IllegalStateException")
        void markPaid_fromCanceled_throwsIllegalState() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act & Assert
            assertThrows(IllegalStateException.class, state::markPaid);
        }

        @Test
        @DisplayName("start() exception chứa thông tin 'CANCELED'")
        void start_fromCanceled_exceptionMessageContainsCanceled() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::start);

            // Assert
            assertTrue(ex.getMessage().contains("CANCELED"));
        }

        @Test
        @DisplayName("markPaid() exception chứa thông tin 'CANCELED'")
        void markPaid_fromCanceled_exceptionMessageContainsCanceled() {
            // Arrange
            AuctionState state = CanceledState.INSTANCE;

            // Act
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, state::markPaid);

            // Assert
            assertTrue(ex.getMessage().contains("CANCELED"));
        }

        // -- Singleton identity -----------------------------------------------

        @Test
        @DisplayName("INSTANCE là singleton — cùng một tham chiếu")
        void instance_isSingleton() {
            // Assert
            assertSame(CanceledState.INSTANCE, CanceledState.INSTANCE);
        }
    }

    // =========================================================================
    // AuctionTransitionTest — kiểm tra toàn bộ luồng state machine
    // =========================================================================

    @Nested
    @DisplayName("AuctionTransitionTest — full lifecycle flows")
    class AuctionTransitionTest {

        // -- Happy path flows -------------------------------------------------

        @Test
        @DisplayName("Luồng đầy đủ: OPEN → RUNNING → FINISHED → PAID")
        void fullFlow_openToRunningToFinishedToPaid() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            state = state.start();
            assertEquals(Auction.AuctionStatus.RUNNING, state.getStatus());

            state = state.close(true);
            assertEquals(Auction.AuctionStatus.FINISHED, state.getStatus());

            state = state.markPaid();

            // Assert
            assertEquals(Auction.AuctionStatus.PAID, state.getStatus());
            assertSame(PaidState.INSTANCE, state);
        }

        @Test
        @DisplayName("Luồng OPEN → RUNNING → CANCELED (không có winner)")
        void flow_openToRunningToCanceled_noWinner() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            state = state.start();
            state = state.close(false);

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, state.getStatus());
            assertSame(CanceledState.INSTANCE, state);
        }

        @Test
        @DisplayName("Luồng OPEN → CANCELED (hủy trước khi bắt đầu)")
        void flow_openToCanceled_cancelBeforeStart() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            state = state.cancel();

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, state.getStatus());
            assertSame(CanceledState.INSTANCE, state);
        }

        @Test
        @DisplayName("Luồng OPEN → RUNNING → CANCELED (admin hủy khi đang chạy)")
        void flow_openToRunningToCanceled_adminCancel() {
            // Arrange
            AuctionState state = OpenState.INSTANCE;

            // Act
            state = state.start();
            state = state.cancel();

            // Assert
            assertEquals(Auction.AuctionStatus.CANCELED, state.getStatus());
            assertSame(CanceledState.INSTANCE, state);
        }

        // -- State consistency after transition -------------------------------

        @Test
        @DisplayName("Sau khi OPEN → RUNNING, state trước không bị thay đổi (immutability)")
        void transition_doesNotMutateOriginalState() {
            // Arrange
            AuctionState openState = OpenState.INSTANCE;

            // Act
            AuctionState runningState = openState.start();

            // Assert
            assertEquals(Auction.AuctionStatus.OPEN, openState.getStatus());
            assertEquals(Auction.AuctionStatus.RUNNING, runningState.getStatus());
        }

        @Test
        @DisplayName("Sau khi RUNNING → FINISHED, state RUNNING vẫn trả về RUNNING")
        void transition_runningToFinished_runningStateUnchanged() {
            // Arrange
            AuctionState runningState = RunningState.INSTANCE;

            // Act
            AuctionState finishedState = runningState.close(true);

            // Assert
            assertEquals(Auction.AuctionStatus.RUNNING, runningState.getStatus());
            assertEquals(Auction.AuctionStatus.FINISHED, finishedState.getStatus());
        }

        // -- Singleton consistency across flows --------------------------------

        @Test
        @DisplayName("Hai luồng khác nhau từ RUNNING → FINISHED đều trả về cùng một FinishedState")
        void twoFlows_runningToFinished_returnSameFinishedInstance() {
            // Arrange
            AuctionState running1 = RunningState.INSTANCE;
            AuctionState running2 = RunningState.INSTANCE;

            // Act
            AuctionState finished1 = running1.close(true);
            AuctionState finished2 = running2.close(true);

            // Assert
            assertSame(finished1, finished2);
        }

        @Test
        @DisplayName("Hai luồng khác nhau từ OPEN → CANCELED đều trả về cùng một CanceledState")
        void twoFlows_openToCanceled_returnSameCanceledInstance() {
            // Arrange
            AuctionState open1 = OpenState.INSTANCE;
            AuctionState open2 = OpenState.INSTANCE;

            // Act
            AuctionState canceled1 = open1.cancel();
            AuctionState canceled2 = open2.cancel();

            // Assert
            assertSame(canceled1, canceled2);
        }

        // -- Terminal state rejection after full flow -------------------------

        @Test
        @DisplayName("PAID là terminal: mọi action sau khi PAID đều ném exception")
        void paidState_allActions_throwAfterFullFlow() {
            // Arrange
            AuctionState state = OpenState.INSTANCE
                    .start()
                    .close(true)
                    .markPaid();

            // Assert — tất cả 4 action đều bị từ chối
            assertThrows(IllegalStateException.class, state::start);
            assertThrows(IllegalStateException.class, () -> state.close(true));
            assertThrows(IllegalStateException.class, () -> state.close(false));
            assertThrows(IllegalStateException.class, state::cancel);
            assertThrows(IllegalStateException.class, state::markPaid);
        }

        @Test
        @DisplayName("CANCELED là terminal: start/close/markPaid đều ném exception")
        void canceledState_allActionsExceptSelfCancel_throwAfterFullFlow() {
            // Arrange
            AuctionState state = OpenState.INSTANCE.cancel();

            // Assert — 4 action bị từ chối, cancel idempotent
            assertThrows(IllegalStateException.class, state::start);
            assertThrows(IllegalStateException.class, () -> state.close(true));
            assertThrows(IllegalStateException.class, () -> state.close(false));
            assertThrows(IllegalStateException.class, state::markPaid);
            assertDoesNotThrow(state::cancel); // cancel idempotent
        }

        // -- close(boolean) branching logic -----------------------------------

        @Test
        @DisplayName("close(true) và close(false) từ RUNNING trả về state khác nhau")
        void close_fromRunning_hasWinnerFlag_determinesNextState() {
            // Arrange
            AuctionState running = RunningState.INSTANCE;

            // Act
            AuctionState withWinner    = running.close(true);
            AuctionState withoutWinner = running.close(false);

            // Assert
            assertNotSame(withWinner, withoutWinner);
            assertEquals(Auction.AuctionStatus.FINISHED, withWinner.getStatus());
            assertEquals(Auction.AuctionStatus.CANCELED, withoutWinner.getStatus());
        }

        // -- AuctionStatus enum mapping integrity -----------------------------

        @Test
        @DisplayName("Mỗi state trả về AuctionStatus khác nhau (không bị trùng)")
        void eachState_returnsDistinctAuctionStatus() {
            // Arrange & Act
            Auction.AuctionStatus openStatus     = OpenState.INSTANCE.getStatus();
            Auction.AuctionStatus runningStatus  = RunningState.INSTANCE.getStatus();
            Auction.AuctionStatus finishedStatus = FinishedState.INSTANCE.getStatus();
            Auction.AuctionStatus paidStatus     = PaidState.INSTANCE.getStatus();
            Auction.AuctionStatus canceledStatus = CanceledState.INSTANCE.getStatus();

            // Assert
            assertNotEquals(openStatus,     runningStatus);
            assertNotEquals(openStatus,     finishedStatus);
            assertNotEquals(openStatus,     paidStatus);
            assertNotEquals(openStatus,     canceledStatus);
            assertNotEquals(runningStatus,  finishedStatus);
            assertNotEquals(runningStatus,  paidStatus);
            assertNotEquals(runningStatus,  canceledStatus);
            assertNotEquals(finishedStatus, paidStatus);
            assertNotEquals(finishedStatus, canceledStatus);
            assertNotEquals(paidStatus,     canceledStatus);
        }
    }
}