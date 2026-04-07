package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.IRatingService;

/** Factory cụ thể để tạo các đối tượng Electronics. */
public class ElectronicsFactory extends ItemFactory {

    public ElectronicsFactory(IRatingService ratingService) { super(ratingService); }

    /**
     * @param args args[0]: brand (String), args[1]: warrantyMonths (int), args[2]: condition (String)
     */
    @Override
    protected Item createProduct(String name, String description, double startingPrice,
                                 NormalUser seller, Object... args) {
        String brand = (String) args[0];
        int warrantyMonths = (int) args[1];
        String condition = (String) args[2];
        return Electronics.create(name, description, startingPrice, seller, brand, warrantyMonths, condition);
    }
}