package com.group13.auction.model.user;

/**
 * Factory chuyên tạo Seller.
 */
public class SellerFactory extends UserFactory {
    @Override
    protected User createProduct(String username, String password, String email, Object... args) {
        return Seller.create(username, password, email);
    }
}
