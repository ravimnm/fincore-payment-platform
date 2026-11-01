package com.fincore.backend.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincore.backend.dto.UserSearchResult;
import com.fincore.backend.service.UserSearchService;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserSearchService userSearchService;

    public UserController(UserSearchService userSearchService) {
        this.userSearchService = userSearchService;
    }

    // GET /users/search?q=ravi | 9876543210 | 42 | username
    @GetMapping("/search")
    public List<UserSearchResult> search(
            @RequestParam("q") String q,
            Authentication authentication) {

        return userSearchService.search(q, authentication.getName());
    }
}
