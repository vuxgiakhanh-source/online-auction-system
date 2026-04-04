package com.group13.auction.factory;

import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.user.Seller;
import com.group13.auction.model.item.Vehicle;

/**
 * Factory tạo Item — tập trung validate và khởi tạo.
 * ID được sinh bởi Entity (UUID).
 */
public class ItemFactory {
  /** Utility class — không cho khởi tạo. */

  private ItemFactory() {}

  /**
   * Tạo Electronics.
   *
   * @param name           tên sản phẩm
   * @param description    mô tả
   * @param startingPrice  giá khởi điểm
   * @param seller         người bán
   * @param brand          thương hiệu
   * @param warrantyMonths số tháng bảo hành
   * @param condition      tình trạng
   * @return Electronics mới
   */
  public static Item createElectronics(String name, String description,
      double startingPrice, Seller seller,
      String brand, int warrantyMonths, String condition) {
    validateCommon(name, startingPrice, seller);
    return new Electronics(name, description, startingPrice,
        seller, brand, warrantyMonths, condition);
  }

  /**
   * Tạo Art.
   *
   * @param name          tên tác phẩm
   * @param description   mô tả
   * @param startingPrice giá khởi điểm
   * @param seller        người bán
   * @param artist        nghệ sĩ
   * @param yearCreated   năm sáng tác
   * @param medium        chất liệu
   * @return Art mới
   */
  public static Item createArt(String name, String description,
      double startingPrice, Seller seller,
      String artist, int yearCreated, String medium) {
    validateCommon(name, startingPrice, seller);
    return new Art(name, description, startingPrice,
        seller, artist, yearCreated, medium);
  }

  /**
   * Tạo Vehicle.
   *
   * @param name          tên phương tiện
   * @param description   mô tả
   * @param startingPrice giá khởi điểm
   * @param seller        người bán
   * @param manufacturer  hãng sản xuất
   * @param year          năm sản xuất
   * @param mileage       số km đã đi
   * @return Vehicle mới
   */
  public static Item createVehicle(String name, String description,
      double startingPrice, Seller seller,
      String manufacturer, int year, double mileage) {
    validateCommon(name, startingPrice, seller);
    return new Vehicle(name, description, startingPrice,
        seller, manufacturer, year, mileage);
  }

  private static void validateCommon(String name, double startingPrice,
      Seller seller) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Tên sản phẩm không được trống.");
    }
    if (startingPrice <= 0) {
      throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0.");
    }
    if (seller == null) {
      throw new IllegalArgumentException("Thông tin người bán không hợp lệ.");
    }

    /** Hệ thống tự check quyền của Seller */
    if (!seller.canCreateAuction()) {
        throw new IllegalStateException("Tài khoản người bán đang bị khóa hoặc uy tín thấp.");
    }
  }
}