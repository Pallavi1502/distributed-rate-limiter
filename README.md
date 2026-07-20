# Distributed Rate Limiter
 
A Redis-backed, annotation-driven rate limiter for Spring Boot applications. Add a single annotation to any controller method to enforce distributed, per-client rate limits — no external gateway or third-party service required.
 
## Why this exists
 
Rate limiting a single instance is easy — an in-memory counter works fine. The moment you run more than one instance behind a load balancer, an in-memory counter is wrong: each instance enforces its own limit, so a client can multiply its effective quota by the number of instances. This project solves that by pushing the counting into Redis, which every instance shares, and doing the check-and-decrement as a single atomic operation so concurrent requests from different instances can't race each other into granting extra tokens.
 
## How it works
 
- **`@RateLimit(limit, window)`** — a method-level annotation you place on any controller endpoint, specifying how many requests are allowed (`limit`) in a given time window in seconds (`window`).
- **`RateLimitAspect`** — a Spring AOP `@Before` advice that intercepts any method carrying `@RateLimit`. It reads the client IP, HTTP method, and request URI off the current request, and builds a composite Redis key: token_bucket:{httpMethod}:{requestURI}:{clientIp}
  Each endpoint gets its own independent bucket per client, so hitting your rate limit on one route doesn't affect another.
- **`RateLimiterService`** — executes a **Lua script** on Redis via `RedisTemplate` to run the token bucket algorithm atomically:
  1. Fetch the bucket's current token count and last-updated timestamp from a Redis hash.
  2. If the bucket doesn't exist yet, initialize it full.
  3. Otherwise, refill tokens based on elapsed time (`refill_rate = capacity / window`), capped at the bucket's capacity.
  4. If at least one token is available, consume it and allow the request; otherwise, reject it.
  5. Reset the key's TTL so idle buckets expire automatically instead of accumulating in Redis forever.
  Running this as a single Lua script means the read-refill-check-write cycle happens as one atomic operation on the Redis server — there's no window where two concurrent requests could both read the same token count and both get allowed.
- **`RateLimitExceededException` + `GlobalExceptionHandler`** — when a request is rejected, the aspect throws an exception that's caught globally and turned into a structured `429 Too Many Requests` JSON response, rather than a raw stack trace.
## Tech stack
 
- Java 17, Spring Boot
- Spring AOP (AspectJ) for annotation interception
- Spring Data Redis (`RedisTemplate`) for atomic Lua script execution
- Maven
## Getting started
 
### Prerequisites
- Java 17+
- Maven
- A running Redis instance (locally, via Docker, or managed)
```bash
docker run -d --name redis -p 6379:6379 redis:7
```
 
### Configuration
 
In `application.yml` (or `application.properties`), point the app at your Redis instance:
 
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```
 
### Run
 
```bash
./mvnw spring-boot:run
```
 
## Usage
 
Annotate any controller method you want protected:
 
```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
 
    @RateLimit(limit = 10, window = 60) // 10 requests per 60 seconds, per client
    @GetMapping
    public List<Order> getOrders() {
        ...
    }
}
```
 
If `limit` and `window` are omitted, they default to 10 requests per 60-second window.
 
When the limit is exceeded, the API responds with:
 
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please try again later.",
  "timestamp": "2026-07-20T14:32:10.512"
}
```
 
## Design notes
 
- **Why token bucket over fixed/sliding window?** Token bucket allows short bursts up to the bucket's capacity while still enforcing a steady average rate, which is closer to how real client traffic behaves than a hard fixed-window cutoff.
- **Why a Lua script instead of separate Redis calls?** Redis executes Lua scripts atomically — no other command can run in between. Doing the refill math and token decrement as separate `GET`/`SET` calls from the application would open a race window between concurrent requests; the script closes it entirely.
- **Why key by method + URI + client IP?** So each endpoint is limited independently, and each client's usage of that endpoint is tracked independently, rather than one global counter shared by all clients or all routes.
- **TTL safety net:** every bucket's Redis key expires after `2 × window` seconds of inactivity, so idle clients don't leave stale keys in Redis indefinitely.
## Project structure
 
```
com.project.distributed_rate_limiter
├── annotation/    → @RateLimit
├── aspect/        → RateLimitAspect (AOP interception)
├── service/       → RateLimiterService (Lua script execution)
├── config/        → RedisConfig
└── exception/     → RateLimitExceededException, GlobalExceptionHandler, ErrorResponse
```
 
## Possible extensions
 
- Support additional algorithms (sliding window log, sliding window counter) selectable via the annotation
- Rate limit by API key / authenticated user ID instead of (or in addition to) IP
- Expose current bucket state via an admin/metrics endpoint
- Configurable key resolution strategy (custom `KeyResolver` interface) for multi-tenant setups
