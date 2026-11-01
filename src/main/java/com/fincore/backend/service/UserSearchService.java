package com.fincore.backend.service;

import java.util.List;
import java.util.Locale;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.dto.UserSearchResult;
import com.fincore.backend.entity.User;
import com.fincore.backend.repository.UserRepository;
import com.fincore.backend.repository.WalletRepository;

@Service
public class UserSearchService {

    private static final int MAX_RESULTS = 10;
    private static final int MAX_QUERY_LENGTH = 50;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public UserSearchService(
            UserRepository userRepository,
            WalletRepository walletRepository) {

        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    /**
     * Finds people the caller can pay, by user ID, name, username,
     * mobile number or wallet number. The caller is never returned.
     *
     * Results are cached per requesting username and normalized query.
     */
    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = "userSearch",
        keyGenerator = "userSearchCacheKeyGenerator"
    )
    public List<UserSearchResult> search(
            String rawQuery,
            String currentUsername) {

        String q = rawQuery == null ? "" : rawQuery.trim();

        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }

        boolean numericOnly = q.matches("\\d+");

        if (q.isEmpty() || (!numericOnly && q.length() < 3)) {
            return List.of();
        }

        User me = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String lower = q.toLowerCase(Locale.ROOT);

        String escaped = lower
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        String text = "%" + escaped + "%";

        return walletRepository
                .search(
                    text,
                    phonePattern(q),
                    q,
                    me.getId(),
                    PageRequest.of(0, MAX_RESULTS)
                )
                .stream()
                .map(UserSearchResult::from)
                .toList();
    }

    /**
     * Builds a LIKE pattern for the digits-only form of a phone number.
     */
    private String phonePattern(String q) {

        final String NO_MATCH = "no-phone-match";

        if (!q.matches("[0-9\\s+\\-()]+")) {
            return NO_MATCH;
        }

        String digits = q.replaceAll("\\D", "");

        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }

        if (digits.length() < 3) {
            return NO_MATCH;
        }

        return "%" + digits + "%";
    }
}