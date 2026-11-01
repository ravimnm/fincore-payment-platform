package com.fincore.backend.dto;

import com.fincore.backend.entity.User;
import com.fincore.backend.entity.Wallet;
import java.io.Serializable;

/**
 * Public, minimal view of a user returned by the search endpoint.
 * Deliberately excludes email and the full phone number.
 */
public class UserSearchResult implements Serializable{

    private final Long userId;
    private final Long walletId;
    private final String walletNumber;
    private final String displayName;
    private final String username;
    private final String maskedPhone;

    public UserSearchResult(
            Long userId,
            Long walletId,
            String walletNumber,
            String displayName,
            String username,
            String maskedPhone) {

        this.userId = userId;
        this.walletId = walletId;
        this.walletNumber = walletNumber;
        this.displayName = displayName;
        this.username = username;
        this.maskedPhone = maskedPhone;
    }

    public static UserSearchResult from(Wallet wallet) {

        User user = wallet.getUser();

        return new UserSearchResult(
                user.getId(),
                wallet.getId(),
                wallet.getWalletNumber(),
                user.getDisplayName(),
                user.getUsername(),
                maskPhone(user.getPhone())
        );
    }

    private static String maskPhone(String phone) {

        if (phone == null) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");

        if (digits.length() < 4) {
            return "••••";
        }

        return "•••••• " + digits.substring(digits.length() - 4);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public String getWalletNumber() {
        return walletNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }
}
