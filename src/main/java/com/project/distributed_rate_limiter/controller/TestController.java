package com.project.distributed_rate_limiter.controller;

import com.project.distributed_rate_limiter.annotation.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TestController {

    @GetMapping("/test")
    @RateLimit(limit = 5, window = 60)
    public ResponseEntity<Map<String, String>> testRateLimiter() {
        return ResponseEntity.ok(Map.of(
                "status", "Success",
                "message", "Hello from the protected SDE route! You have tokens left."
        ));
    }

    @GetMapping("/search")
    @RateLimit(limit = 2, window = 60)
    public ResponseEntity<String> searchRoute() {
        return ResponseEntity.ok("Search results");
    }
}
