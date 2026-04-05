package com.group13.auction.model.user;

import com.group13.auction.model.user.Bidder;
import com.group13.auction.model.user.User;

/**
 * Factory chuyên tạo Bidder.
 */
public class BidderFactory extends UserFactory {
    @Override
    protected User createProduct(String username, String password, String email, Object... args) {
        return Bidder.create(username, password, email);
    }
}
