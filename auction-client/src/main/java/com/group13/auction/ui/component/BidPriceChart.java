package com.group13.auction.ui.component;

import com.group13.auction.common.dto.bid.BidDTOs;
import java.util.List;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Biểu đồ giá bid đơn giản (không cần javafx-charts).
 */
public final class BidPriceChart extends Region {

    private final Canvas canvas = new Canvas();
    private List<BidDTOs.BidChartPointDTO> points = List.of();

    public BidPriceChart() {
        getChildren().add(canvas);
        widthProperty().addListener((obs, o, w) -> redraw());
        heightProperty().addListener((obs, o, h) -> redraw());
    }

    public void setPoints(List<BidDTOs.BidChartPointDTO> points) {
        this.points = points != null ? points : List.of();
        redraw();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    private void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#f8fafc"));
        g.fillRect(0, 0, w, h);

        if (points.isEmpty() || w < 40 || h < 40) {
            g.setFill(Color.web("#64748b"));
            g.setFont(Font.font(13));
            g.fillText("Chưa có dữ liệu bid", 12, 24);
            return;
        }

        long min = points.stream().mapToLong(BidDTOs.BidChartPointDTO::getPrice).min().orElse(0);
        long max = points.stream().mapToLong(BidDTOs.BidChartPointDTO::getPrice).max().orElse(1);
        if (max <= min) {
            max = min + 1;
        }

        double padL = 56;
        double padR = 16;
        double padT = 16;
        double padB = 28;
        double chartW = w - padL - padR;
        double chartH = h - padT - padB;

        g.setStroke(Color.web("#e2e8f0"));
        g.strokeLine(padL, padT + chartH, padL + chartW, padT + chartH);
        g.strokeLine(padL, padT, padL, padT + chartH);

        g.setFill(Color.web("#64748b"));
        g.setFont(Font.font(10));
        g.fillText(formatPrice(min), 4, padT + chartH);
        g.fillText(formatPrice(max), 4, padT + 10);

        g.setStroke(Color.web("#2563eb"));
        g.setLineWidth(2);
        int n = points.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            long price = points.get(i).getPrice();
            xs[i] = padL + (n == 1 ? chartW / 2 : chartW * i / (n - 1));
            ys[i] = padT + chartH - chartH * (price - min) / (double) (max - min);
            if (i > 0) {
                g.strokeLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
            }
        }
        g.setFill(Color.web("#2563eb"));
        for (int i = 0; i < n; i++) {
            g.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
        }
    }

    private static String formatPrice(long v) {
        if (v >= 1_000_000) {
            return (v / 1_000_000) + "M";
        }
        if (v >= 1_000) {
            return (v / 1_000) + "K";
        }
        return String.valueOf(v);
    }
}
