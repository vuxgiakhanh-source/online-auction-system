package com.group13.auction.service;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item.ItemCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service sort danh sách auction đang diễn ra.
 * Hỗ trợ sort theo: currentPrice, số người xem, phân
 * loại sản phẩm.
 */
public class AuctionSortService {

    private static final Logger log = LoggerFactory.getLogger(AuctionSortService.class);

    /**
     * Sort auction theo giá hiện tại (cao → thấp).
     *
     * @param auctions danh sách auction cần sort
     * @return danh sách đã sort (bản copy mới)
     */
    public List<Auction> sortByCurrentPriceDesc(List<Auction> auctions) {
        return auctions.stream()
                .sorted(Comparator.comparingDouble(Auction::getCurrentPrice).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Sort auction theo giá hiện tại (thấp → cao).
     *
     * @param auctions danh sách auction cần sort
     * @return danh sách đã sort (bản copy mới)
     */
    public List<Auction> sortByCurrentPriceAsc(List<Auction> auctions) {
        return auctions.stream()
                .sorted(Comparator.comparingDouble(Auction::getCurrentPrice))
                .collect(Collectors.toList());
    }

    /**
     * Sort auction theo số người xem (nhiều → ít).
     *
     * @param auctions danh sách auction cần sort
     * @return danh sách đã sort (bản copy mới)
     */
    public List<Auction> sortByViewerCountDesc(List<Auction> auctions) {
        return auctions.stream()
                .sorted(Comparator.comparingInt(Auction::getViewerCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Lọc auction theo phân loại sản phẩm.
     *
     * @param auctions danh sách auction cần lọc
     * @param category phân loại sản phẩm
     * @return danh sách đã lọc
     */
    public List<Auction> filterByCategory(List<Auction> auctions, ItemCategory category) {
        log.debug("filterByCategory: category={}, total={}", category, auctions.size());
        return auctions.stream()
                .filter(a -> a.getItem().getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Sort auction theo phân loại sản phẩm (thứ tự enum).
     *
     * @param auctions danh sách auction cần sort
     * @return danh sách đã sort theo category
     */
    public List<Auction> sortByCategory(List<Auction> auctions) {
        return auctions.stream()
                .sorted(Comparator.comparing(a -> a.getItem().getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * Sort kết hợp: theo category trước, trong cùng category sort theo currentPrice giảm dần.
     *
     * @param auctions danh sách auction cần sort
     * @return danh sách đã sort
     */
    public List<Auction> sortByCategoryThenPrice(List<Auction> auctions) {
        return auctions.stream()
                .sorted(
                        Comparator.<Auction, ItemCategory>comparing(a -> a.getItem().getCategory())
                                .thenComparing(Comparator.comparingDouble(Auction::getCurrentPrice).reversed()))
                .collect(Collectors.toList());
    }

    /**
     * Sort kết hợp: theo số người xem giảm dần, trong cùng viewer count sort theo price giảm dần.
     *
     * @param auctions danh sách auction cần sort
     * @return danh sách đã sort
     */
    public List<Auction> sortByViewersThenPrice(List<Auction> auctions) {
        return auctions.stream()
                .sorted(
                        Comparator.comparingInt(Auction::getViewerCount)
                                .reversed()
                                .thenComparing(Comparator.comparingDouble(Auction::getCurrentPrice).reversed()))
                .collect(Collectors.toList());
    }
}