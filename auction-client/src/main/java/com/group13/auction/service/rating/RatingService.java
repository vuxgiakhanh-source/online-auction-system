package com.group13.auction.service.rating;

import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.RatingViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.rating.RatingHistoryViewModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý rating ở phía client.
 *
 * <p>Client chỉ validate input cơ bản và gọi server. Các rule nghiệp vụ như ai được đánh giá ai,
 * đánh giá sau phiên nào, chống đánh giá trùng hoặc tính điểm trung bình là trách nhiệm server.
 */
public final class RatingService {

    private static final double MIN_RATING = 1.0;
    private static final double MAX_RATING = 5.0;
    private static final int MAX_COMMENT_LENGTH = 500;

    private final ClientNetworkFacade networkFacade;

    /** Tạo service dùng network facade mặc định của ứng dụng. */
    public RatingService() {
        this(ClientNetworkFacade.getDefault());
    }

    /**
     * Tạo service với dependency truyền vào, hữu ích cho test.
     *
     * @param networkFacade facade tầng network
     */
    public RatingService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /**
     * Gửi đánh giá cho Seller.
     *
     * @param sellerId mã Seller được đánh giá
     * @param auctionId mã phiên đấu giá liên quan
     * @param rating điểm đánh giá từ 1 đến 5
     * @param comment nội dung đánh giá
     * @return future hoàn tất khi server xác nhận
     */
    public CompletableFuture<Void> rateSeller(
            String sellerId, String auctionId, double rating, String comment) {
        String validationError = validateRatingRequest(sellerId, auctionId, rating, comment);
        if (validationError != null) {
            return AuctionServiceSupport.failedFuture(validationError);
        }

        RatingDTOs.RateSellerRequestDTO request = new RatingDTOs.RateSellerRequestDTO();
        request.setSellerId(sellerId.trim());
        request.setAuctionId(auctionId.trim());
        request.setRating(rating);
        request.setComment(normalizeComment(comment));

        return AuctionServiceSupport.sendVoidRequest(
                networkFacade,
                ClientRequestFactory.rateSeller(request),
                PacketType.RATE_SELLER_SUCCESS,
                "Không gửi được đánh giá Seller.");
    }

    /**
     * Gửi đánh giá cho Bidder.
     *
     * @param bidderId mã Bidder được đánh giá
     * @param auctionId mã phiên đấu giá liên quan
     * @param rating điểm đánh giá từ 1 đến 5
     * @param comment nội dung đánh giá
     * @return future hoàn tất khi server xác nhận
     */
    public CompletableFuture<Void> rateBidder(
            String bidderId, String auctionId, double rating, String comment) {
        String validationError = validateRatingRequest(bidderId, auctionId, rating, comment);
        if (validationError != null) {
            return AuctionServiceSupport.failedFuture(validationError);
        }

        RatingDTOs.RateBidderRequestDTO request = new RatingDTOs.RateBidderRequestDTO();
        request.setBidderId(bidderId.trim());
        request.setAuctionId(auctionId.trim());
        request.setRating(rating);
        request.setComment(normalizeComment(comment));

        return AuctionServiceSupport.sendVoidRequest(
                networkFacade,
                ClientRequestFactory.rateBidder(request),
                PacketType.RATE_BIDDER_SUCCESS,
                "Không gửi được đánh giá Bidder.");
    }

    /**
     * Lấy lịch sử rating của một người dùng.
     *
     * @param userId mã người dùng cần xem rating
     * @return future chứa danh sách rating view model
     */
    public CompletableFuture<List<RatingHistoryViewModel>> getRatings(String userId) {
        if (isBlank(userId)) {
            return AuctionServiceSupport.failedFuture("Thiếu mã người dùng cần xem rating.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getUserRatings(userId.trim()),
                        PacketType.GET_USER_RATINGS_SUCCESS,
                        RatingDTOs.RatingHistoryDTO.class,
                        "Không tải được lịch sử rating.")
                .thenApply(RatingViewModelMapper::toHistoryViewModels);
    }

    private String validateRatingRequest(
            String targetUserId, String auctionId, double rating, String comment) {
        if (isBlank(targetUserId)) {
            return "Thiếu mã người được đánh giá.";
        }
        if (isBlank(auctionId)) {
            return "Thiếu mã phiên đấu giá liên quan.";
        }
        if (rating < MIN_RATING || rating > MAX_RATING) {
            return "Điểm đánh giá phải nằm trong khoảng 1 đến 5.";
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            return "Nội dung đánh giá không được vượt quá 500 ký tự.";
        }

        return null;
    }

    private String normalizeComment(String comment) {
        return comment == null ? "" : comment.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}