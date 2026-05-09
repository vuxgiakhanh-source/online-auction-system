package com.group13.auction.service.iservice;

import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;

/**
 * Hợp đồng xử lý báo cáo chất lượng hàng hóa sau phiên đấu giá.
 *
 * <ol>
 *   <li>Winner gọi {@link #submitReport} -> report ở PENDING.</li>
 *   <li>Admin gọi {@link #approveReport} or {@link #rejectReport}.</li>
 *   <li>Nếu approve -> trừ rating Seller, hoàn tiền winner.</li>
 * </ol>
 */
public interface IQualityReportService {

    /**
     * Winner gửi báo cáo chất lượng hàng hóa.
     *
     * @param report report đã được tạo bởi winner (phải có ít nhất 1 ảnh đính kèm)
     * @return report vừa được lưu
     * @throws IllegalArgumentException nếu report null hoặc không có ảnh
     */
    QualityReport submitReport(QualityReport report);

    /**
     * Admin phê duyệt QualityReport.
     *
     * <p>Khi approve: chuyển status -> APPROVED, set deadline 24h cho Seller,
     * trừ rating Seller, hoàn tiền cho Winner, notify Staff.
     *
     * @param admin  admin thực hiện phê duyệt
     * @param report report cần approve (phải đang ở PENDING)
     * @param auction phiên liên quan (để lấy seller, finalPrice)
     * @throws IllegalStateException nếu report không ở PENDING
     */
    void approveReport(Admin admin, QualityReport report, Auction auction);

    /**
     * Admin từ chối QualityReport.
     *
     * @param admin  admin thực hiện từ chối
     * @param report report cần reject (phải đang ở PENDING)
     * @throws IllegalStateException nếu report không ở PENDING
     */
    void rejectReport(Admin admin, QualityReport report);
}