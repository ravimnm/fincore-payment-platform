package com.fincore.backend.cache;

import java.lang.reflect.Method;
import java.util.Locale;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

@Component("userSearchCacheKeyGenerator")
public class UserSearchCacheKeyGenerator implements KeyGenerator {

    private static final int MAX_QUERY_LENGTH = 50;

    @Override
    public Object generate(Object target, Method method, Object... params) {

        String rawQuery = (String) params[0];
        String username = (String) params[1];

        String query = rawQuery == null ? "" : rawQuery.trim();

        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }

        query = query.toLowerCase(Locale.ROOT);

        return "user-search:" + username + ":" + query;
    }
}