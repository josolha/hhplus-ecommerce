//package com.sparta.ecommerce.infrastructure.config;
//
//import com.sparta.ecommerce.domain.product.repository.ProductRepository;
//import com.sparta.ecommerce.domain.user.entity.BalanceHistory;
//import com.sparta.ecommerce.domain.user.entity.User;
//import com.sparta.ecommerce.domain.user.repository.BalanceHistoryRepository;
//import com.sparta.ecommerce.domain.user.repository.UserRepository;
//import com.sparta.ecommerce.domain.user.vo.Balance;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;
//
//@Slf4j
//@Configuration
//@RequiredArgsConstructor
//public class DataInitializer {
//
////    // === local 환경 (개발자 PC) ===
////    @Bean
////    @Profile("local")
////    public CommandLineRunner initLocalData(
////            UserRepository userRepository,
////            ProductRepository productRepository,
////            BalanceHistoryRepository balanceHistoryRepository
////    ) {
////        return args -> {
////            log.info("========================================");
////            log.info("🏠 로컬 환경 - 테스트 데이터 대량 생성 시작");
////            log.info("========================================");
////
////            // 사용자 10명 생성 + 충전 이력
////            for (int i = 1; i <= 10; i++) {
////                User user = User.builder()
////                        .name("테스트유저" + i)
////                        .email("user" + i + "@test.com")
////                        .balance(new Balance(100000L * i))
////                        .build();
////                user = userRepository.save(user);
////
////                // 각 사용자마다 2-3회 충전 이력 생성
////                long finalBalance = 100000L * i;
////                String[] paymentMethods = {"CARD", "BANK_TRANSFER", "KAKAO_PAY", "TOSS"};
////
////                // 첫 번째 충전 (최종 잔액의 40%)
////                long firstCharge = (long) (finalBalance * 0.4);
////                balanceHistoryRepository.save(
////                        BalanceHistory.builder()
////                                .userId(user.getUserId())
////                                .amount(firstCharge)
////                                .previousBalance(0L)
////                                .currentBalance(firstCharge)
////                                .paymentMethod(paymentMethods[i % paymentMethods.length])
////                                .build()
////                );
////
////                // 두 번째 충전 (최종 잔액의 30%)
////                long secondCharge = (long) (finalBalance * 0.3);
////                balanceHistoryRepository.save(
////                        BalanceHistory.builder()
////                                .userId(user.getUserId())
////                                .amount(secondCharge)
////                                .previousBalance(firstCharge)
////                                .currentBalance(firstCharge + secondCharge)
////                                .paymentMethod(paymentMethods[(i + 1) % paymentMethods.length])
////                                .build()
////                );
////
////                // 세 번째 충전 (나머지)
////                long thirdCharge = finalBalance - firstCharge - secondCharge;
////                balanceHistoryRepository.save(
////                        BalanceHistory.builder()
////                                .userId(user.getUserId())
////                                .amount(thirdCharge)
////                                .previousBalance(firstCharge + secondCharge)
////                                .currentBalance(finalBalance)
////                                .paymentMethod(paymentMethods[(i + 2) % paymentMethods.length])
////                                .build()
////                );
////            }
//
///*            // 상품 20개 생성
//            String[] categories = {"전자기기", "의류", "식품", "도서", "생활용품"};
//            for (int i = 1; i <= 20; i++) {
//                Product product = Product.builder()
//                        .name("테스트상품" + i)
//                        .price(10000.0 * i)
//                        .stock(new Stock(100))
//                        .category(categories[i % categories.length])
//                        .description("테스트용 상품 설명 " + i)
//                        .build();
//                productRepository.save(product);
//            }*/
//
//            log.info("✅ 사용자 {} 명 생성 완료", userRepository.count());
//            log.info("✅ 충전 이력 {} 건 생성 완료", balanceHistoryRepository.count());
//            //log.info("✅ 상품 {} 개 생성 완료", productRepository.count());
//            log.info("========================================");
//        };
//    }
//
//    // === dev 환경 (개발 서버) ===
//    @Bean
//    @Profile("dev")
//    public CommandLineRunner initDevData(
//            UserRepository userRepository,
//            ProductRepository productRepository
//    ) {
//        return args -> {
//            log.info("========================================");
//            log.info("🔧 개발 서버 환경 - 기본 데이터만 생성");
//            log.info("========================================");
//
//            // 관리자 계정 1개
//            User admin = User.builder()
//                    .name("관리자")
//                    .balance(new Balance(10000000L))
//                    .build();
//            userRepository.save(admin);
//
//            // 테스트 상품 3개
///*
//            productRepository.save(Product.builder()
//                    .name("아이폰 15")
//                    .price(1500000.0)
//                    .stock(new Stock(50))
//                    .category("전자기기")
//                    .description("테스트용 아이폰")
//                    .build());
//
//            productRepository.save(Product.builder()
//                    .name("맥북 프로")
//                    .price(2500000.0)
//                    .stock(new Stock(30))
//                    .category("전자기기")
//                    .description("테스트용 맥북")
//                    .build());
//
//            productRepository.save(Product.builder()
//                    .name("에어팟 프로")
//                    .price(350000.0)
//                    .stock(new Stock(100))
//                    .category("전자기기")
//                    .description("테스트용 에어팟")
//                    .build());
//*/
//
//            log.info("✅ 관리자 계정 생성 완료");
//            //log.info("✅ 기본 상품 {} 개 생성 완료", productRepository.count());
//            log.info("========================================");
//        };
//    }
//
//    // === prod 환경 (운영 서버) ===
//    @Bean
//    @Profile("prod")
//    public CommandLineRunner initProdData() {
//        return args -> {
//            log.info("========================================");
//            log.info("🚀 운영 서버 환경 - 데이터 초기화 스킵");
//            log.info("========================================");
//            // 운영에서는 아무것도 하지 않음!
//        };
//    }
//}
//
