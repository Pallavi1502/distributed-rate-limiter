package com.project.distributed_rate_limiter.aspect;

import com.project.distributed_rate_limiter.annotation.RateLimit;
import com.project.distributed_rate_limiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Before("@annotation(com.project.distributed_rate_limiter.annotation.RateLimit)")
    public void interceptRequest(JoinPoint joinPoint){
        // Extract the current HTTP request metadata from the Spring container thread
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;
        HttpServletRequest request = attributes.getRequest();

        String clientIp = request.getRemoteAddr();
        String httpMethod = request.getMethod();
        String requestURI = request.getRequestURI();

        // Use Java Reflection to read the specific annotation parameters configured on the route
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimitAnnotation = method.getAnnotation(RateLimit.class);

        long capacity = rateLimitAnnotation.limit();
        long windowInSeconds = rateLimitAnnotation.window();

        // Construct a highly specific composite key for Redis isolation
        String redisKey = String.format("token_bucket:%s:%s:%s", httpMethod, requestURI, clientIp);

        boolean accessGranted = rateLimiterService.isAllowed(redisKey, capacity, windowInSeconds);

        if (!accessGranted) {
            throw new com.project.distributed_rate_limiter.exception.RateLimitExceededException("Rate limit exceeded. Please try again later.");
        }
    }

}
