package com.group13.auction.model.item;

import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Seller;
import com.group13.auction.service.IRatingService;

/**
 * Factory cụ thể để tạo các đối tượng Electronics.
 */
public class ElectronicsFactory extends ItemFactory {

    public ElectronicsFactory(IRatingService ratingService) {
        super(ratingService);
    }

    /**
     * Thực hiện khởi tạo đối tượng Electronics từ mảng đối số biến đổi.
     * @param args tham số phụ theo thứ tự:
     * args[0]: brand (String) - thương hiệu
     * args[1]: warrantyMonths (int) - số tháng bảo hành
     * args[2]: condition (String) - tình trạng máy
     */
    @Override
    protected Item createProduct(String name, String description, double startingPrice,
                                 Seller seller, Object... args) {
        String brand = (String) args[0];
        int warrantyMonths = (int) args[1];
        String condition = (String) args[2];

        return Electronics.create(name, description, startingPrice, seller, brand, warrantyMonths, condition);
    }
}