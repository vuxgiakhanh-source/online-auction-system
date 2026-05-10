package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;

/** Factory cụ thể để tạo các đối tượng Art. */
public class ArtFactory extends ItemFactory {

    public ArtFactory(IRatingService ratingService) { super(ratingService); }

    /**
     * @param args args[0]: artist (String), args[1]: yearCreated (int), args[2]: medium (String)
     */
    @Override
    protected Item createProduct(String name, String description, long startingPrice,
                                 NormalUser seller, Object... args) {
        String artist = (String) args[0];
        int yearCreated = (int) args[1];
        String medium = (String) args[2];
        return Art.create(name, description, startingPrice, seller, artist, yearCreated, medium);
    }
}