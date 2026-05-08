package com.group13.auction;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.item.Vehicle;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.service.iservice.IRatingService;

import java.time.LocalDateTime;
import java.util.EnumSet;

/**
 * Các helper/fixture dùng chung cho toàn bộ unit test OOP.
 *
 * <p>Cung cấp:
 * <ul>
 *   <li>Fake {@link IRatingService} cho phép kiểm soát kết quả trả về.</li>
 *   <li>Builder nhanh để tạo {@link NormalUser} giả không cần DB.</li>
 *   <li>Builder nhanh để tạo {@link Auction} và {@link Item} con.</li>
 * </ul>
 *
 * <p>Tất cả đều không chạm DB, không cần network.
 */
public final class TestFixture {

    private TestFixture() {}

    // Fake IRatingService

    /**
     * Fake IRatingService: mọi Seller đều được phép tạo phiên đấu giá.
     * Dùng cho test luồng bình thường (happy path).
     */
    public static IRatingService ratingServiceAllowAll() {
        return new IRatingService() {
            @Override
            public boolean isEligible(User user) {
                return true;
            }

            @Override
            public boolean canSellerCreateAuction(User seller) {
                return true;
            }

            @Override
            public void rewardBidder(NormalUser bidder) {}

            @Override
            public void rewardSeller(User seller) {}

            @Override
            public void penalizeLatePayment(NormalUser bidder) {}

            @Override
            public void penalizeSeller(User seller) {}

            @Override
            public void checkAndRestoreSuspended(User user) {}
        };
    }

    /**
     * Fake IRatingService: mọi Seller đều bị từ chối tạo phiên đấu giá.
     * Dùng để test trường hợp Seller bị khoá.
     */
    public static IRatingService ratingServiceDenyAll() {
        return new IRatingService() {
            @Override
            public boolean isEligible(User user) {
                return false;
            }

            @Override
            public boolean canSellerCreateAuction(User seller) {
                return false;
            }

            @Override
            public void rewardBidder(NormalUser bidder) {}

            @Override
            public void rewardSeller(User seller) {}

            @Override
            public void penalizeLatePayment(NormalUser bidder) {}

            @Override
            public void penalizeSeller(User seller) {}

            @Override
            public void checkAndRestoreSuspended(User user) {}
        };
    }

    // NormalUser builder (không cần DB)

    /**
     * Tạo NormalUser với role BIDDER mặc định, balance = 0.
     * username phải >= 8 ký tự, password >= 8 ký tự.
     */
    public static NormalUser normalBidder(String username) {
        return NormalUser.reconstitute(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                0L,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    /**
     * Tạo NormalUser có đầy đủ cả role BIDDER lẫn SELLER.
     * Có sẵn balance để tham gia đấu giá.
     */
    public static NormalUser normalSeller(String username) {
        return NormalUser.reconstitute(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                10_000_000L,
                0L,
                EnumSet.of(User.UserRole.BIDDER, User.UserRole.SELLER),
                false,
                false,
                null);
    }

    /**
     * Tạo NormalUser với balance tùy chỉnh và role BIDDER.
     */
    public static NormalUser bidderWithBalance(String username, long balance) {
        return NormalUser.reconstitute(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                username,
                User.hashPassword("password1"),
                username + "@test.com",
                User.AccountStatus.ACTIVE,
                3.0,
                balance,
                0L,
                EnumSet.of(User.UserRole.BIDDER),
                false,
                false,
                null);
    }

    // Item builder trực tiếp (bypass ItemFactory, không cần IRatingService)

    /**
     * Tạo Art trực tiếp qua reconstitute — dùng khi chỉ cần Item đơn giản.
     */
    public static Art art(String name, long startingPrice, NormalUser seller) {
        return Art.reconstitute(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                name,
                "Mô tả " + name,
                startingPrice,
                seller,
                "Nghệ sĩ Test",
                2020,
                "Sơn dầu");
    }

    /**
     * Tạo Electronics trực tiếp qua reconstitute.
     */
    public static Electronics electronics(String name, long startingPrice, NormalUser seller) {
        return Electronics.reconstitute(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                name,
                "Mô tả " + name,
                startingPrice,
                seller,
                "Samsung",
                12,
                "Mới 100%");
    }

    /**
     * Tạo Vehicle trực tiếp qua reconstitute.
     */
    public static Vehicle vehicle(String name, long startingPrice, NormalUser seller) {
        return Vehicle.reconstitute(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                name,
                "Mô tả " + name,
                startingPrice,
                seller,
                "Toyota",
                2019,
                50000.0);
    }

    // Auction builder

    /**
     * Tạo Auction OPEN với Art item và reserve = startingPrice * 2.
     */
    public static Auction openAuction(NormalUser seller, long startingPrice) {
        Item item = art("Tranh Test", startingPrice, seller);
        return Auction.create(
                item,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1),
                startingPrice * 2);
    }

    /**
     * Tạo Auction RUNNING (đã chuyển trạng thái).
     */
    public static Auction runningAuction(NormalUser seller, long startingPrice) {
        Auction auction = openAuction(seller, startingPrice);
        auction.transitionToRunning();
        return auction;
    }
}