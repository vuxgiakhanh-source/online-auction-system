package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import java.util.List;

/** Factory cụ thể để tạo các đối tượng Art. */
public class ArtFactory extends ItemFactory {

  public ArtFactory(IRatingService ratingService) {
    super(ratingService);
  }

  /**
   * @param args [0] artist (String), [1] yearCreated (int), [2] medium (String), [3] imageUrls
   *     (List&lt;String&gt;, optional)
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Item createProduct(
      String name, String description, long startingPrice, NormalUser seller, Object... args) {
    String artist = (String) args[0];
    int yearCreated = (int) args[1];
    String medium = (String) args[2];
    List<String> imgs =
        (args.length > 3 && args[3] instanceof List) ? (List<String>) args[3] : List.of();
    return Art.create(name, description, startingPrice, seller, artist, yearCreated, medium, imgs);
  }
}
