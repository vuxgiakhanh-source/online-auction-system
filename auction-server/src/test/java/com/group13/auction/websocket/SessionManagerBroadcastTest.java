package com.group13.auction.websocket;

import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.session.SessionManager;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm hành vi "topic / broadcast" trong hệ WebSocket tự triển khai:
 * {@link SessionManager} map connection → session, theo dõi {@code watchingAuctionIds},
 * và broadcast có chọn lọc (theo phiên, theo role admin, toàn hệ thống, v.v.).
 *
 * <p>Không dùng STOMP broker — contract tương đương subscribe theo {@code auctionId}
 * + broadcast bus nội bộ.
 *
 * <p><b>Lưu ý:</b> {@link SessionManager#getInstance()} là singleton — mỗi test phải
 * {@code unregister} mọi WebSocket mock đã {@code register} để không rò state sang test khác.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManager — broadcast, sendToUser, lifecycle (unit, đầy đủ)")
class SessionManagerBroadcastTest {

    @Mock(lenient = true)
    WebSocket wsWatcherA;

    @Mock(lenient = true)
    WebSocket wsWatcherB;

    @Mock(lenient = true)
    WebSocket wsOtherAuction;

    @Mock(lenient = true)
    WebSocket wsAdmin;

    @Mock(lenient = true)
    WebSocket wsGuest;

    SessionManager sm;

    @BeforeEach
    void setUp() {
        sm = SessionManager.getInstance();
        when(wsWatcherA.isOpen()).thenReturn(true);
        when(wsWatcherB.isOpen()).thenReturn(true);
        when(wsOtherAuction.isOpen()).thenReturn(true);
        when(wsAdmin.isOpen()).thenReturn(true);
        when(wsGuest.isOpen()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        sm.unregister(wsWatcherA);
        sm.unregister(wsWatcherB);
        sm.unregister(wsOtherAuction);
        sm.unregister(wsAdmin);
        sm.unregister(wsGuest);
    }

    // ── broadcastToAuction ───────────────────────────────────────────────────

    @Nested
    @DisplayName("broadcastToAuction — chỉ client đang watch đúng auctionId")
    class BroadcastToAuction {

        @Test
        @DisplayName("Given hai client cùng watch auc-X When broadcast auc-X Then cả hai nhận WebSocket.send(JSON)")
        void twoWatchers_sameAuction_bothReceive() {
            sm.register(wsWatcherA);
            sm.register(wsWatcherB);
            sm.addAuctionWatcher(wsWatcherA, "auc-X");
            sm.addAuctionWatcher(wsWatcherB, "auc-X");

            sm.broadcastToAuction("auc-X", Packet.of(PacketType.PING));

            verify(wsWatcherA, atLeastOnce()).send(anyString());
            verify(wsWatcherB, atLeastOnce()).send(anyString());
        }

        @Test
        @DisplayName("Given một client watch auc-1, một client watch auc-2 When broadcast auc-1 Then chỉ watcher auc-1 nhận")
        void differentAuctions_onlyMatchingWatcherReceives() {
            sm.register(wsWatcherA);
            sm.register(wsOtherAuction);
            sm.addAuctionWatcher(wsWatcherA, "auc-1");
            sm.addAuctionWatcher(wsOtherAuction, "auc-2");

            sm.broadcastToAuction("auc-1", Packet.of(PacketType.BID_UPDATE));

            verify(wsWatcherA, atLeastOnce()).send(anyString());
            verify(wsOtherAuction, never()).send(anyString());
        }

        @Test
        @DisplayName("Given client chưa addAuctionWatcher When broadcast Then không gọi WebSocket.send")
        void notWatching_doesNotReceive() {
            sm.register(wsGuest);
            // không addAuctionWatcher

            sm.broadcastToAuction("auc-99", Packet.of(PacketType.PING));

            verify(wsGuest, never()).send(anyString());
        }

        @Test
        @DisplayName("Given 3 client cùng watch When broadcast Then đúng 3 lần WebSocket.send (encode một lần, fan-out)")
        void threeWatchers_allReceive() {
            sm.register(wsWatcherA);
            sm.register(wsWatcherB);
            sm.register(wsOtherAuction);
            sm.addAuctionWatcher(wsWatcherA, "auc-multi");
            sm.addAuctionWatcher(wsWatcherB, "auc-multi");
            sm.addAuctionWatcher(wsOtherAuction, "auc-multi");

            sm.broadcastToAuction("auc-multi", Packet.of(PacketType.AUCTION_ENDED_UPDATE));

            verify(wsWatcherA, times(1)).send(anyString());
            verify(wsWatcherB, times(1)).send(anyString());
            verify(wsOtherAuction, times(1)).send(anyString());
        }
    }

    // ── broadcastToAuctionExcept ─────────────────────────────────────────────

    @Nested
    @DisplayName("broadcastToAuctionExcept — loại trừ một userId")
    class BroadcastExcept {

        @Test
        @DisplayName("Given excludeUserId=user-2 When broadcast Then user-1 nhận, user-2 không")
        void excludesListedUser() {
            sm.register(wsWatcherA);
            sm.register(wsWatcherB);
            sm.addAuctionWatcher(wsWatcherA, "auc-100");
            sm.addAuctionWatcher(wsWatcherB, "auc-100");
            sm.authenticate(wsWatcherA, "user-1", "alice", "NORMAL_USER");
            sm.authenticate(wsWatcherB, "user-2", "bob", "NORMAL_USER");

            sm.broadcastToAuctionExcept("auc-100", Packet.of(PacketType.PING), "user-2");

            verify(wsWatcherA, atLeastOnce()).send(anyString());
            verify(wsWatcherB, never()).send(anyString());
        }

        @Test
        @DisplayName("Given excludeUserId=null When broadcast Then mọi watcher (kể cả đã login) đều nhận")
        void nullExclude_allWatchersReceive() {
            sm.register(wsWatcherA);
            sm.register(wsWatcherB);
            sm.addAuctionWatcher(wsWatcherA, "auc-101");
            sm.addAuctionWatcher(wsWatcherB, "auc-101");
            sm.authenticate(wsWatcherA, "u1", "a", "NORMAL_USER");
            sm.authenticate(wsWatcherB, "u2", "b", "NORMAL_USER");

            sm.broadcastToAuctionExcept("auc-101", Packet.of(PacketType.PING), null);

            verify(wsWatcherA, atLeastOnce()).send(anyString());
            verify(wsWatcherB, atLeastOnce()).send(anyString());
        }

        @Test
        @DisplayName("Given session chưa authenticate (userId null) nhưng đang watch When exclude user khác Then vẫn nhận (FIX NPE)")
        void unauthenticatedWatcher_stillReceivesWhenNotExcluded() {
            sm.register(wsGuest);
            sm.addAuctionWatcher(wsGuest, "auc-102");
            // không authenticate → getUserId() == null

            sm.broadcastToAuctionExcept("auc-102", Packet.of(PacketType.PING), "someone-else");

            verify(wsGuest, atLeastOnce()).send(anyString());
        }
    }

    // ── broadcastToAdmins / broadcastAll / broadcastAuthenticated ─────────────

    @Nested
    @DisplayName("Broadcast theo role / toàn hệ thống")
    class BroadcastByRoleAndGlobal {

        @Test
        @DisplayName("broadcastToAdmins — chỉ ADMIN_STAFF / ADMIN_MASTER nhận")
        void adminsOnly_receive() {
            sm.register(wsAdmin);
            sm.register(wsWatcherA);
            sm.authenticate(wsAdmin, "adm-1", "staff", "ADMIN_STAFF");
            sm.authenticate(wsWatcherA, "u-1", "user", "NORMAL_USER");

            sm.broadcastToAdmins(Packet.of(PacketType.SELLER_ROLE_APPROVED_NOTIFY));

            verify(wsAdmin, atLeastOnce()).send(anyString());
            verify(wsWatcherA, never()).send(anyString());
        }

        @Test
        @DisplayName("broadcastAll — mọi connection đã register đều nhận (kể cả chưa login)")
        void broadcastAll_hitsEverySocket() {
            sm.register(wsGuest);
            sm.register(wsWatcherA);

            sm.broadcastAll(Packet.of(PacketType.SYSTEM_ANNOUNCEMENT));

            verify(wsGuest, atLeastOnce()).send(anyString());
            verify(wsWatcherA, atLeastOnce()).send(anyString());
        }

        @Test
        @DisplayName("broadcastAuthenticated — chỉ session đã authenticate (có trong byUserId)")
        void broadcastAuthenticated_onlyLoggedIn() {
            sm.register(wsGuest);
            sm.register(wsWatcherA);
            sm.authenticate(wsWatcherA, "u-9", "logged", "NORMAL_USER");
            // wsGuest: chưa authenticate

            sm.broadcastAuthenticated(Packet.of(PacketType.PING));

            verify(wsWatcherA, atLeastOnce()).send(anyString());
            verify(wsGuest, never()).send(anyString());
        }
    }

    // ── sendToUser & getUserIdsWatchingAuction ────────────────────────────────

    @Nested
    @DisplayName("sendToUser — định tuyến theo userId")
    class SendToUser {

        @Test
        @DisplayName("When user online Then gửi qua session.send (encode Packet)")
        void onlineUser_receives() {
            sm.register(wsWatcherA);
            sm.authenticate(wsWatcherA, "target-user", "tina", "NORMAL_USER");

            sm.sendToUser("target-user", Packet.of(PacketType.PING));

            // ClientSession.send → WebSocket.send(JSON string)
            ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
            verify(wsWatcherA, times(1)).send(json.capture());
            assertThat(json.getValue()).contains("PING");
        }

        @Test
        @DisplayName("When user offline Then không throw và không gọi send")
        void offlineUser_noSend() {
            sm.sendToUser("ghost-user-id", Packet.of(PacketType.PING));
            // không register socket nào — không có mock để verify; chỉ đảm bảo không crash
            assertThat(sm.isOnline("ghost-user-id")).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserIdsWatchingAuction — danh sách user đang watch + authenticated")
    class WatchingUserIds {

        @Test
        @DisplayName("Given hai user authenticated và watch cùng auction When query Then trả đúng 2 userId")
        void listsAuthenticatedWatchersOnly() {
            sm.register(wsWatcherA);
            sm.register(wsWatcherB);
            sm.addAuctionWatcher(wsWatcherA, "auc-list");
            sm.addAuctionWatcher(wsWatcherB, "auc-list");
            sm.authenticate(wsWatcherA, "id-a", "a", "NORMAL_USER");
            sm.authenticate(wsWatcherB, "id-b", "b", "NORMAL_USER");

            List<String> ids = sm.getUserIdsWatchingAuction("auc-list");

            assertThat(ids).containsExactlyInAnyOrder("id-a", "id-b");
        }

        @Test
        @DisplayName("Given watcher chưa login When query Then không có trong list")
        void guestWatcher_excludedFromList() {
            sm.register(wsGuest);
            sm.addAuctionWatcher(wsGuest, "auc-guest");
            // không authenticate

            List<String> ids = sm.getUserIdsWatchingAuction("auc-guest");

            assertThat(ids).isEmpty();
        }
    }

    // ── Lifecycle: authenticate, deauthenticate, replace session ─────────────

    @Nested
    @DisplayName("Session lifecycle — register / authenticate / deauthenticate")
    class Lifecycle {

        @Test
        @DisplayName("authenticate — map userId; isOnline true")
        void authenticate_tracksUser() {
            sm.register(wsWatcherA);
            sm.authenticate(wsWatcherA, "u-100", "alice", "NORMAL_USER");

            assertThat(sm.isOnline("u-100")).isTrue();
            assertThat(sm.getByUserId("u-100")).isNotNull();
        }

        @Test
        @DisplayName("deauthenticate — gỡ userId khỏi map; isOnline false")
        void deauthenticate_clearsUserMapping() {
            sm.register(wsWatcherA);
            sm.authenticate(wsWatcherA, "u-101", "bob", "NORMAL_USER");
            sm.deauthenticate(wsWatcherA);

            assertThat(sm.isOnline("u-101")).isFalse();
        }

        @Test
        @DisplayName("Cùng userId login tab mới — session cũ bị close và gỡ connection")
        void secondLoginSameUserId_closesOldSession() {
            sm.register(wsWatcherA);
            sm.register(wsWatcherB);
            sm.authenticate(wsWatcherA, "same", "user", "NORMAL_USER");

            sm.authenticate(wsWatcherB, "same", "user", "NORMAL_USER");

            verify(wsWatcherA).close(1000, "Logged in from another location");
            assertThat(sm.getByUserId("same")).isNotNull();
            assertThat(sm.getByConnection(wsWatcherB)).isSameAs(sm.getByUserId("same"));
        }
    }
}
