# Code Style & Architecture Rules

## 1. ADR-001 Progressive Feature-First Architecture

Base package: `com.seproduction.legendsandtraitors`

- **Feature Directory Structure**: Features start flat and only subdivide into standard subpackages (`controller/`, `handler/`, `service/`, `repository/`, `model/`) once complexity warrants (e.g., > 5–7 files, dual REST+WS protocols, or rich entity models).
- **Encapsulation & Visibility**:
  - Keep internal implementation classes **package-private** (`class ComponentName`).
  - Only expose **interfaces**, DTOs/records, and top-level entry points as `public`.
  - Never import another feature package's repository or internal entities directly. Communication between features must pass through public service interfaces or DTOs.

## 2. Java 21 & Spring Boot Standards

- **Language Level**: Java 21 LTS with Virtual Threads enabled (`spring.threads.virtual.enabled=true`).
- **Data Carriers**: Use Java `record` types for immutable REST request/response bodies and STOMP message payloads.
- **Dependency Injection**: Use constructor injection via Lombok `@RequiredArgsConstructor` with `private final` fields. Avoid `@Autowired` on field declarations.
- **Virtual Thread Safety**: Guard against carrier thread pinning in game logic. Avoid `synchronized` blocks and methods; use `ReentrantLock` or atomic types if thread safety is required.
- **Fail-Fast Validation**: Use Spring's `Assert.notNull()`, `Assert.hasText()`, and `Assert.isTrue()` for defensive parameter checking.

## 3. Testing Standards

- **Unit Tests**:
  - Fast, isolated tests must use `@ExtendWith(MockitoExtension.class)` without `@SpringBootTest`.
  - Must run within `< 1.0s` without requiring live external backing services (Postgres/Redis).
- **Slice & Integration Tests**:
  - Redis tests use `@DataRedisTest`.
  - JPA tests use `@DataJpaTest`.
  - Require local Docker containers `tc-postgres-dev` and `tc-redis-dev` to be running.
- **Documentation Preservation**:
  - Never modify, shorten, or remove existing Javadoc comments or architectural explanations in pre-existing files during feature development.
