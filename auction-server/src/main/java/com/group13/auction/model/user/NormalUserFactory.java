package com.group13.auction.model.user;

/** Factory tạo NormalUser */
public class NormalUserFactory extends UserFactory<NormalUser> {

  @Override
  protected NormalUser createProduct(
      String username, String password, String email, Object... args) {
    return NormalUser.create(username, password, email);
  }
}
