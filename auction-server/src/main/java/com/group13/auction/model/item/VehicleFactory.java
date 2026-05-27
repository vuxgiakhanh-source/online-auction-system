package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import java.util.List;

/** Factory cụ thể để tạo các đối tượng Vehicle. */
public class VehicleFactory extends ItemFactory {

  public VehicleFactory(IRatingService ratingService) {
    super(ratingService);
  }

  /**
   * @param args [0] manufacturer (String), [1] year (int), [2] mileage (double), [3] imageUrls
   *     (List&lt;String&gt;, optional)
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Item createProduct(
      String name, String description, long startingPrice, NormalUser seller, Object... args) {
    String manufacturer = (String) args[0];
    int year = (int) args[1];
    double mileage = (double) args[2];
    List<String> imgs =
        (args.length > 3 && args[3] instanceof List) ? (List<String>) args[3] : List.of();
    return Vehicle.create(
        name, description, startingPrice, seller, manufacturer, year, mileage, imgs);
  }
}
