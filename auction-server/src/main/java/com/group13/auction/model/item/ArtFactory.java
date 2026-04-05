package com.group13.auction.model.item;

import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Seller;
import com.group13.auction.service.IRatingService;

/**
 * Factory cụ thể để tạo các đối tượng Art.
 */
public class ArtFactory extends ItemFactory {

    public ArtFactory(IRatingService ratingService) {
        super(ratingService);
    }

    /**
     * Thực hiện khởi tạo đối tượng Art từ mảng đối số biến đổi.
     * @param args tham số phụ theo thứ tự:
     * args[0]: artist (String) - tên nghệ sĩ
     * args[1]: yearCreated (int) - năm sáng tác
     * args[2]: medium (String) - chất liệu
     */
    @Override
    protected Item createProduct(String name, String description, double startingPrice,
                                 Seller seller, Object... args) {
        String artist = (String) args[0];
        int yearCreated = (int) args[1];
        String medium = (String) args[2];

        return Art.create(name, description, startingPrice, seller, artist, yearCreated, medium);
    }
}
