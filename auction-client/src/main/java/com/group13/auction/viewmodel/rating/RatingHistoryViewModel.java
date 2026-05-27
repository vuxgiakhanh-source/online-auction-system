package com.group13.auction.viewmodel.rating;

/** View model hiển thị một đánh giá trong lịch sử rating. */
public class RatingHistoryViewModel {

  private final String ratingId;
  private final String reviewerId;
  private final String targetUserId;
  private final String auctionId;
  private final int score;
  private final String scoreText;
  private final String comment;
  private final String createdAtText;

  /**
   * Tạo dữ liệu hiển thị cho một rating.
   *
   * @param ratingId mã rating
   * @param reviewerId mã người đánh giá
   * @param targetUserId mã người được đánh giá
   * @param auctionId mã phiên đấu giá liên quan
   * @param score điểm đánh giá
   * @param scoreText điểm đánh giá dạng text
   * @param comment nội dung đánh giá
   * @param createdAtText thời gian tạo đã format
   */
  public RatingHistoryViewModel(
      String ratingId,
      String reviewerId,
      String targetUserId,
      String auctionId,
      int score,
      String scoreText,
      String comment,
      String createdAtText) {
    this.ratingId = ratingId;
    this.reviewerId = reviewerId;
    this.targetUserId = targetUserId;
    this.auctionId = auctionId;
    this.score = score;
    this.scoreText = scoreText;
    this.comment = comment;
    this.createdAtText = createdAtText;
  }

  public String getRatingId() {
    return ratingId;
  }

  public String getReviewerId() {
    return reviewerId;
  }

  public String getTargetUserId() {
    return targetUserId;
  }

  public String getAuctionId() {
    return auctionId;
  }

  public int getScore() {
    return score;
  }

  public String getScoreText() {
    return scoreText;
  }

  public String getComment() {
    return comment;
  }

  public String getCreatedAtText() {
    return createdAtText;
  }
}
