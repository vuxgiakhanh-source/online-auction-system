package com.group13.auction.service.report;

import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.mapper.ReportViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.network.http.ImageUploadService;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.admin.QualityReportReviewViewModel;
import com.group13.auction.viewmodel.report.QualityReportViewModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Service xử lý báo cáo chất lượng sản phẩm ở phía client.
 *
 * <p>Client chỉ validate input cơ bản, gửi request đúng PacketType/DTO và map response sang view
 * model. Việc xác minh báo cáo, hoàn tiền, phạt Seller hoặc cập nhật trạng thái là trách nhiệm
 * server.
 */
public final class QualityReportService {

    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final ClientNetworkFacade networkFacade;

    /** Tạo service dùng network facade mặc định của ứng dụng. */
    public QualityReportService() {
        this(ClientNetworkFacade.getDefault());
    }

    /**
     * Tạo service với dependency truyền vào, hữu ích cho test.
     *
     * @param networkFacade facade tầng network
     */
    public QualityReportService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /**
     * Gửi báo cáo chất lượng sản phẩm.
     *
     * @param auctionId mã phiên đấu giá liên quan
     * @param description mô tả vấn đề
     * @param evidenceUrls danh sách URL bằng chứng, có thể rỗng
     * @return future chứa report view model server trả về
     */
    public CompletableFuture<QualityReportViewModel> submitQualityReport(
            String auctionId, String description, List<String> evidenceUrls) {
        String validationError = validateReportRequest(auctionId, description);
        if (validationError != null) {
            return AuctionServiceSupport.failedFuture(validationError);
        }

        ReportDTOs.QualityReportRequestDTO request = new ReportDTOs.QualityReportRequestDTO();
        request.setAuctionId(auctionId.trim());
        request.setDescription(description.trim());
        request.setEvidenceUrls(evidenceUrls == null ? List.of() : evidenceUrls);

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.submitQualityReport(request),
                        PacketType.SUBMIT_QUALITY_REPORT_SUCCESS,
                        ReportDTOs.QualityReportDTO.class,
                        "Không gửi được báo cáo chất lượng.")
                .thenApply(ReportViewModelMapper::toViewModel);
    }

    /**
     * Lấy danh sách báo cáo chất lượng cho Admin review.
     *
     * @return future chứa danh sách report review view model
     */
    public CompletableFuture<List<QualityReportReviewViewModel>> getQualityReportsForAdmin() {
        if (!currentUserIsAdmin()) {
            return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.adminGetQualityReports(),
                        PacketType.ADMIN_GET_QUALITY_REPORTS_SUCCESS,
                        ReportDTOs.QualityReportDTO[].class,
                        "Không tải được danh sách báo cáo chất lượng.")
                .thenApply(reports -> ReportViewModelMapper.toReviewViewModels(Arrays.asList(reports)));
    }

    /**
     * Duyệt chấp nhận một báo cáo chất lượng.
     *
     * @param reportId mã báo cáo
     * @return future chứa thông báo kết quả server trả về
     */
    public CompletableFuture<String> resolveQualityReport(String reportId) {
        if (!currentUserIsAdmin()) {
            return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
        }
        if (isBlank(reportId)) {
            return AuctionServiceSupport.failedFuture("Thiếu mã báo cáo cần duyệt.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.adminApproveQualityReport(reportId.trim()),
                        PacketType.ADMIN_APPROVE_QUALITY_REPORT_SUCCESS,
                        ReportDTOs.QualityReportResultDTO.class,
                        "Không duyệt được báo cáo chất lượng.")
                .thenApply(this::formatReportResult);
    }

    /**
     * Từ chối một báo cáo chất lượng.
     *
     * @param reportId mã báo cáo
     * @return future hoàn tất khi server xác nhận
     */
    public CompletableFuture<Void> rejectQualityReport(String reportId) {
        if (!currentUserIsAdmin()) {
            return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
        }
        if (isBlank(reportId)) {
            return AuctionServiceSupport.failedFuture("Thiếu mã báo cáo cần từ chối.");
        }

        return AuctionServiceSupport.sendVoidRequest(
                networkFacade,
                ClientRequestFactory.adminRejectQualityReport(reportId.trim()),
                PacketType.ADMIN_REJECT_QUALITY_REPORT_SUCCESS,
                "Không từ chối được báo cáo chất lượng.");
    }

    /**
     * Upload ảnh bằng chứng rồi gửi báo cáo chất lượng.
     *
     * <p>Flow đúng của backend/common là upload ảnh trước để lấy URL, sau đó gửi các URL đó trong
     * {@code QualityReportRequestDTO.evidenceUrls}.
     *
     * @param auctionId mã phiên đấu giá liên quan
     * @param description mô tả vấn đề
     * @param imagePaths danh sách ảnh local cần upload
     * @return future chứa report view model server trả về
     */
    public CompletableFuture<QualityReportViewModel> submitQualityReportWithImages(
            String auctionId, String description, List<Path> imagePaths) {
        String validationError = validateReportRequest(auctionId, description);
        if (validationError != null) {
            return AuctionServiceSupport.failedFuture(validationError);
        }

        List<Path> safeImagePaths = imagePaths == null ? List.of() : imagePaths;

        return CompletableFuture
                .supplyAsync(() -> uploadEvidenceImages(safeImagePaths))
                .thenCompose(evidenceUrls -> submitQualityReport(auctionId, description, evidenceUrls));
    }

    private List<String> uploadEvidenceImages(List<Path> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return List.of();
        }

        List<String> uploadedUrls = new ArrayList<>();
        ImageUploadService uploadService = ImageUploadService.getInstance();

        for (Path imagePath : imagePaths) {
            if (imagePath == null) {
                continue;
            }

            try {
                uploadedUrls.add(uploadService.upload(imagePath));
            } catch (IOException exception) {
                throw new CompletionException(
                        "Không upload được ảnh bằng chứng: " + imagePath.getFileName(), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CompletionException("Quá trình upload ảnh bị gián đoạn.", exception);
            }
        }

        return uploadedUrls;
    }

    private String validateReportRequest(String auctionId, String description) {
        if (isBlank(auctionId)) {
            return "Thiếu mã phiên đấu giá cần báo cáo.";
        }
        if (isBlank(description)) {
            return "Vui lòng nhập mô tả báo cáo.";
        }

        String normalizedDescription = description.trim();
        if (normalizedDescription.length() < MIN_DESCRIPTION_LENGTH) {
            return "Mô tả báo cáo cần có ít nhất 10 ký tự.";
        }
        if (normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            return "Mô tả báo cáo không được vượt quá 1000 ký tự.";
        }

        return null;
    }

    private String formatReportResult(ReportDTOs.QualityReportResultDTO result) {
        if (result == null) {
            return "Quality report has been resolved.";
        }

        String reportId = result.getReportId() == null ? "--" : result.getReportId();
        return "Quality report has been resolved. Report ID: " + reportId;
    }

    private boolean currentUserIsAdmin() {
        return AppContext.getInstance()
                .getSessionManager()
                .getCurrentSession()
                .map(session -> session.isAdmin())
                .orElse(false);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}