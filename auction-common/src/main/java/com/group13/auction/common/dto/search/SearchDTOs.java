package com.group13.auction.common.dto.search;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import java.util.List;

/**
 * Namespace class chứa toàn bộ DTO liên quan đến tìm kiếm sản phẩm.
 *
 * <ul>
 *   <li>{@link ItemSearchRequestDTO} — Client gửi lên kèm packet {@code SEARCH_ITEMS}.
 *   <li>{@link ItemSearchResponseDTO} — Server trả về kèm packet {@code SEARCH_ITEMS_SUCCESS}.
 * </ul>
 */
public final class SearchDTOs {

  private SearchDTOs() {}
  // Request
  /**
   * Payload Client → Server cho packet {@code SEARCH_ITEMS}.
   *
   * <p>Các trường hỗ trợ:
   *
   * <ul>
   *   <li>{@code keyword} — từ khóa tìm theo tên sản phẩm (không phân biệt hoa thường, LIKE).
   *   <li>{@code page} — trang hiện tại, bắt đầu từ 0 (mặc định 0).
   *   <li>{@code size} — số kết quả mỗi trang (mặc định 20, tối đa 100).
   *   <li>{@code sortBy} — cột sắp xếp: {@code currentPrice | endTime | createdAt | itemName} (mặc
   *       định {@code createdAt}).
   *   <li>{@code sortDir} — chiều sắp xếp: {@code ASC | DESC} (mặc định {@code DESC}).
   * </ul>
   */
  public static class ItemSearchRequestDTO {

    private String keyword;
    private int page = 0;
    private int size = 20;
    private String sortBy = "createdAt";
    private String sortDir = "DESC";

    /**
     * Phạm vi lọc theo hoạt động của user hiện tại:
     *
     * <ul>
     *   <li>{@code ALL} — tìm kiếm toàn bộ (mặc định).
     *   <li>{@code OWNED} — chỉ phiên do user hiện tại tạo.
     *   <li>{@code JOINED} — chỉ phiên user đã tham gia đặt cọc.
     *   <li>{@code WATCHING} — chỉ phiên user đang theo dõi.
     * </ul>
     *
     * Null hoặc trống tương đương {@code ALL}.
     */
    private String scopeFilter;

    public ItemSearchRequestDTO() {}

    public String getKeyword() {
      return keyword;
    }

    public void setKeyword(String keyword) {
      this.keyword = keyword;
    }

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = Math.max(0, page);
    }

    public int getSize() {
      return size;
    }

    public void setSize(int size) {
      this.size = (size <= 0 || size > 100) ? 20 : size;
    }

    public String getSortBy() {
      return sortBy;
    }

    public void setSortBy(String sortBy) {
      this.sortBy = sortBy;
    }

    public String getSortDir() {
      return sortDir;
    }

    public void setSortDir(String sortDir) {
      this.sortDir = sortDir;
    }

    public String getScopeFilter() {
      return scopeFilter;
    }

    public void setScopeFilter(String scopeFilter) {
      this.scopeFilter = scopeFilter;
    }
  }
  // Response
  /**
   * Payload Server → Client cho packet {@code SEARCH_ITEMS_SUCCESS}.
   *
   * <p>Tái sử dụng {@link AuctionDTOs.AuctionDTO} làm item đơn vị để client không cần mapper mới —
   * cùng ViewModel với danh sách auction thông thường.
   */
  public static class ItemSearchResponseDTO {

    private List<AuctionDTOs.AuctionDTO> auctions;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private String keyword;

    public ItemSearchResponseDTO() {}

    public ItemSearchResponseDTO(
        List<AuctionDTOs.AuctionDTO> auctions,
        long totalElements,
        int currentPage,
        int pageSize,
        String keyword) {
      this.auctions = auctions;
      this.totalElements = totalElements;
      this.currentPage = currentPage;
      this.pageSize = pageSize;
      this.keyword = keyword;
      this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
    }

    public List<AuctionDTOs.AuctionDTO> getAuctions() {
      return auctions;
    }

    public void setAuctions(List<AuctionDTOs.AuctionDTO> auctions) {
      this.auctions = auctions;
    }

    public long getTotalElements() {
      return totalElements;
    }

    public void setTotalElements(long totalElements) {
      this.totalElements = totalElements;
    }

    public int getTotalPages() {
      return totalPages;
    }

    public void setTotalPages(int totalPages) {
      this.totalPages = totalPages;
    }

    public int getCurrentPage() {
      return currentPage;
    }

    public void setCurrentPage(int currentPage) {
      this.currentPage = currentPage;
    }

    public int getPageSize() {
      return pageSize;
    }

    public void setPageSize(int pageSize) {
      this.pageSize = pageSize;
    }

    public String getKeyword() {
      return keyword;
    }

    public void setKeyword(String keyword) {
      this.keyword = keyword;
    }
  }
}
