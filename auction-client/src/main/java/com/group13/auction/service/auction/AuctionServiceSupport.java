package com.group13.auction.service.auction;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.support.NetworkClientException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Helper nội bộ cho các service đấu giá phía client. */
public final class AuctionServiceSupport {

  private static final long REQUEST_TIMEOUT_SECONDS = 12L;

  private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "auction-service-timeout");
            thread.setDaemon(true);
            return thread;
          });

  private AuctionServiceSupport() {
    // Utility class.
  }

  public static <T> CompletableFuture<T> sendRequest(
      ClientNetworkFacade networkFacade,
      Packet<?> packet,
      PacketType successType,
      Class<T> payloadType,
      String fallbackErrorMessage) {
    Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    Objects.requireNonNull(packet, "packet must not be null");
    Objects.requireNonNull(successType, "successType must not be null");
    Objects.requireNonNull(payloadType, "payloadType must not be null");

    CompletableFuture<T> future = new CompletableFuture<>();

    try {
      ensureConnected(networkFacade);
      networkFacade.sendAndExpect(
          packet,
          (responseType, payload) -> {
            if (responseType == successType) {
              completeSuccess(payload, payloadType, future);
              return;
            }

            future.completeExceptionally(
                new NetworkClientException(extractErrorMessage(payload, fallbackErrorMessage)));
          });

      scheduleTimeout(future, fallbackErrorMessage);
    } catch (RuntimeException exception) {
      future.completeExceptionally(exception);
    }

    return future;
  }

  public static CompletableFuture<Void> sendVoidRequest(
      ClientNetworkFacade networkFacade,
      Packet<?> packet,
      PacketType successType,
      String fallbackErrorMessage) {
    Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    Objects.requireNonNull(packet, "packet must not be null");
    Objects.requireNonNull(successType, "successType must not be null");

    CompletableFuture<Void> future = new CompletableFuture<>();

    try {
      ensureConnected(networkFacade);
      networkFacade.sendAndExpect(
          packet,
          (responseType, payload) -> {
            if (responseType == successType) {
              future.complete(null);
              return;
            }

            future.completeExceptionally(
                new NetworkClientException(extractErrorMessage(payload, fallbackErrorMessage)));
          });

      scheduleTimeout(future, fallbackErrorMessage);
    } catch (RuntimeException exception) {
      future.completeExceptionally(exception);
    }

    return future;
  }

  public static <T> CompletableFuture<T> failedFuture(String message) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(new IllegalArgumentException(message));
    return future;
  }

  private static void ensureConnected(ClientNetworkFacade networkFacade) {
    if (networkFacade.isConnected()) {
      return;
    }

    boolean connected = networkFacade.connectBlocking();
    if (!connected) {
      throw new NetworkClientException(
          "Không kết nối được tới server: " + networkFacade.getServerUri());
    }
  }

  private static <T> void completeSuccess(
      JsonElement payload, Class<T> payloadType, CompletableFuture<T> future) {
    try {
      future.complete(PacketCodec.fromElement(payload, payloadType));
    } catch (RuntimeException exception) {
      future.completeExceptionally(
          new NetworkClientException("Response từ server không hợp lệ.", exception));
    }
  }

  private static <T> void scheduleTimeout(CompletableFuture<T> future, String fallbackMessage) {
    ScheduledFuture<?> timeoutTask =
        TIMEOUT_EXECUTOR.schedule(
            () ->
                future.completeExceptionally(
                    new NetworkClientException("Server không phản hồi. " + fallbackMessage)),
            REQUEST_TIMEOUT_SECONDS,
            TimeUnit.SECONDS);

    future.whenComplete((result, throwable) -> timeoutTask.cancel(false));
  }

  private static String extractErrorMessage(JsonElement payload, String fallbackMessage) {
    if (payload == null || payload.isJsonNull()) {
      return fallbackMessage;
    }

    try {
      ErrorDTO error = PacketCodec.fromElement(payload, ErrorDTO.class);
      if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
        return error.getMessage();
      }
    } catch (RuntimeException ignored) {
      // Dùng fallback nếu payload lỗi không parse được.
    }

    return fallbackMessage;
  }
}
