package com.group13.auction.viewmodel.auction;

/** Một dòng thông số sản phẩm đã format để hiển thị trên màn chi tiết phiên đấu giá. */
public final class ProductSpecificationViewModel {

  private final String label;
  private final String value;

  /** Tạo một dòng thông số sản phẩm. */
  public ProductSpecificationViewModel(String label, String value) {
    this.label = label == null ? "" : label;
    this.value = value == null ? "" : value;
  }

  public String label() {
    return label;
  }

  public String value() {
    return value;
  }
}
