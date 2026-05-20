package com.group13.auction.mapper;

import com.group13.auction.common.dto.payment.PaymentDTOs;
import com.group13.auction.util.CurrencyUtil;
import com.group13.auction.viewmodel.wallet.WalletViewModel;
import java.math.BigDecimal;

/** Mapper chuyển DTO ví từ {@code auction-common} sang view model phía client. */
public final class WalletViewModelMapper {

    private WalletViewModelMapper() {
        // Utility class.
    }

    /**
     * Chuyển wallet balance response sang view model.
     *
     * @param dto response số dư ví từ server
     * @return view model đã format cho UI
     */
    public static WalletViewModel toViewModel(PaymentDTOs.WalletBalanceResponseDTO dto) {
        if (dto == null) {
            return new WalletViewModel(0L, 0L, 0L, "--", "--", "--");
        }

        return new WalletViewModel(
                dto.getBalance(),
                dto.getLockedDeposit(),
                dto.getAvailableBalance(),
                format(dto.getBalance()),
                format(dto.getLockedDeposit()),
                format(dto.getAvailableBalance()));
    }

    private static String format(long amount) {
        return CurrencyUtil.formatVnd(BigDecimal.valueOf(amount));
    }
}