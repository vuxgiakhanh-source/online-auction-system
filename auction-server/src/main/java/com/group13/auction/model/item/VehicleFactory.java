package com.group13.auction.model.item;

import com.group13.auction.model.user.Seller;
import com.group13.auction.service.IRatingService;

/**
 * Factory cụ thể để tạo các đối tượng Vehicle.
 */
public class VehicleFactory extends ItemFactory {

    public VehicleFactory(IRatingService ratingService) {
        super(ratingService);
    }

    @Override
    protected Item createProduct(String name, String description, double startingPrice,
                                 Seller seller, Object... args) {
        /**
         * Thực hiện khởi tạo đối tượng Vehicle từ mảng đối số biến đổi.
         * @param args tham số phụ theo thứ tự:
         * args[0]: manufacturer (String)    hãng sản xuất
         * args[1]: year (int)               năm sản xuất
         * args[2]: mileage (double)         số km đã đi
         */

        String manufacturer = (String) args[0];
        int year = (int) args[1];
        double mileage = (double) args[2];

        return Vehicle.create(name, description, startingPrice, seller,
                manufacturer, year, mileage);
    }
}