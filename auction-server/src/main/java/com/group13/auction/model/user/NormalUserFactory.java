package com.group13.auction.model.user;

/** Factory tạo NormalUser (Bidder mặc định). */
public class NormalUserFactory extends UserFactory {

    @Override
    protected User createProduct(String username, String password,
                                 String email, Object... args) {
        return NormalUser.create(username, password, email);
    }
}