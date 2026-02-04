# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

E-commerce core system built with Spring Boot 3.5.7 and Java 17. This is a backend service for an e-commerce platform implementing product catalog, order/payment, coupon management, and external data integration.

## Build and Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application
./gradlew bootRun

# Clean build
./gradlew clean build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests com.sparta.ecommerce.ClassName

# Run tests with output
./gradlew test --info
```

## Git Commit Convention

**IMPORTANT**: 커밋 생성 시 다음 규칙을 **반드시** 준수하세요.

### 커밋 타입
- `feat`: 새로운 기능 추가
- `fix`: 버그 수정
- `refactor`: 코드 리팩토링
- `docs`: 문서 수정
- `test`: 테스트 추가/수정
- `chore`: 빌드/설정 변경

### 커밋 메시지 형식
```
type: 변경 내용 간단 설명

- 변경된 파일과 구체적인 변경 내용을 상세히 작성
- 여러 변경사항이 있다면 각각 나열
```

### 필수 규칙
1. **절대로** "🤖 Generated with Claude Code" 메시지를 포함하지 마세요
2. **절대로** "Co-Authored-By: Claude <noreply@anthropic.com>" 를 포함하지 마세요
3. 변경된 파일명과 구체적인 변경 내용을 명시하세요
4. 한글로 작성하세요
5. 커밋 메시지는 간결하면서도 변경사항을 정확히 전달해야 합니다

### 예시
```
feat: 사용자 인증 기능 추가

- AuthService.java: JWT 기반 로그인 로직 구현
- SecurityConfig.java: Spring Security 설정 추가
- JwtProvider.java: 토큰 생성 및 검증 유틸리티 작성
```

## System Architecture

### Core Features

1. **Product Management**
   - Product catalog with real-time stock tracking
   - Popular products statistics (last 3 days, Top 5)
   - Caching strategy with 5-minute TTL for popular products

2. **Order & Payment System**
   - Shopping cart functionality
   - Stock verification and deduction (with transaction management)
   - Balance-based payment
   - Coupon discount application
   - Order processing is decoupled from external data transmission

3. **Coupon System**
   - First-come-first-served issuance with limited quantity
   - Distributed lock mechanism (Redis) for concurrency control
   - Coupon validation and usage tracking
   - Duplicate issuance prevention

4. **External Data Integration**
   - Asynchronous order data transmission to external systems
   - Retry mechanism with backoff strategy (max 3 attempts)
   - **Critical**: Order completes successfully even if external transmission fails

### Transaction and Concurrency Strategy

- **Stock management**: `SELECT FOR UPDATE` with transaction isolation
- **Coupon issuance**: Distributed lock (Redis) + database-level locking
- **Balance operations**: Pessimistic locking during payment
- **Order flow**: All-or-nothing transaction (stock deduction, balance deduction, coupon usage)
- **External sync**: Async with retry queue, does not affect order completion

### Error Code System

Error codes are defined in the API design document (`document/API_DESIGN.md`):
- Product errors: `P001` (not found), `P002` (insufficient stock)
- Order errors: `O001` (invalid quantity), `O002` (not found)
- Payment errors: `PAY001` (insufficient balance), `PAY002` (payment failed)
- Coupon errors: `C001` (sold out), `C002` (invalid), `C003` (expired), `C004` (already used)
- Common errors: `COMMON001-004`

When implementing error handling, use these standardized codes for consistency.

## Key Technical Decisions

### Async Processing Pattern
The order completion flow separates order persistence from external data transmission:
1. Order transaction commits first (stock, balance, coupon updates)
2. External data transmission happens asynchronously via queue
3. Retry logic handles transmission failures without blocking user flow

### Concurrency Control
Different strategies are applied based on contention patterns:
- **High contention** (coupon issuance): Distributed lock + DB lock
- **Medium contention** (order placement): Pessimistic locking with `FOR UPDATE`
- **Low contention** (product reads): Optimistic with caching

## API Documentation

Detailed API specifications are in `document/API_DESIGN.md`. All endpoints follow REST conventions with standardized request/response formats and error structures.

Sequence diagrams for major flows are in `document/SEQUENCE_DIAGRAM.md`:
- Product browsing and cart operations
- Order and payment flow (with transaction boundaries)
- Coupon issuance with concurrency
- Balance charging
- Popular products aggregation
- Exception handling scenarios

## Dependencies

- Spring Boot Web (REST API)
- Lombok (boilerplate reduction)
- JUnit Platform (testing)

Database and Redis dependencies are expected to be added as the project develops.

## Package Structure

Base package: `com.sparta.ecommerce`

Expected domain-driven structure (to be implemented):
- Product domain: Product, Stock, Popularity statistics
- Order domain: Order, OrderItem, Cart
- Coupon domain: Coupon, UserCoupon
- User domain: User, Balance
- Payment domain: Payment processing
- External integration: Outbox pattern for data sync
