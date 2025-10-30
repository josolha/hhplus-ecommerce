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
