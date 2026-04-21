package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;

/** Factory cụ thể để tạo các đối tượng Vehicle. */
public class VehicleFactory extends ItemFactory {

    public VehicleFactory(IRatingService ratingService) { super(ratingService); }

    /**
     * @param args args[0]: manufacturer (String), args[1]: year (int), args[2]: mileage (double)
     */
    @Override
    protected Item createProduct(String name, String description, long startingPrice,
                                 NormalUser seller, Object... args) {
        String manufacturer = (String) args[0];
        int year = (int) args[1];
        double mileage = (double) args[2];
        return Vehicle.create(name, description, startingPrice, seller, manufacturer, year, mileage);
    }
}