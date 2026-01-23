#!/bin/bash

# k6 부하 테스트 전체 실행 스크립트
# 사용법: ./run-all-tests.sh [BASE_URL]
# 예시: ./run-all-tests.sh http://localhost:8080

# 색상 코드 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 기본 설정
BASE_URL=${1:-http://localhost:8080}
RESULTS_DIR="./results/$(date +%Y%m%d_%H%M%S)"

# 결과 디렉토리 생성
mkdir -p "$RESULTS_DIR"

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}   k6 부하 테스트 시작${NC}"
echo -e "${BLUE}================================${NC}"
echo -e "Base URL: ${GREEN}$BASE_URL${NC}"
echo -e "결과 저장 경로: ${GREEN}$RESULTS_DIR${NC}"
echo ""

# 서버 상태 체크
echo -e "${YELLOW}[1/4] 서버 상태 확인 중...${NC}"
if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" | grep -q "200\|404"; then
    echo -e "${GREEN}✓ 서버가 정상적으로 실행 중입니다.${NC}"
else
    echo -e "${RED}✗ 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.${NC}"
    exit 1
fi
echo ""

# 1. 쿠폰 발급 테스트
echo -e "${YELLOW}[2/4] 쿠폰 발급 부하 테스트 실행 중...${NC}"
k6 run \
  --env BASE_URL="$BASE_URL" \
  --out json="$RESULTS_DIR/coupon-issue-results.json" \
  coupon-issue-test.js \
  > "$RESULTS_DIR/coupon-issue-output.txt" 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 쿠폰 발급 테스트 완료${NC}"
else
    echo -e "${RED}✗ 쿠폰 발급 테스트 실패${NC}"
fi
echo ""

# 대기 시간 (서버 리소스 복구)
echo -e "${BLUE}서버 리소스 복구를 위해 30초 대기...${NC}"
sleep 30
echo ""

# 2. 주문/결제 테스트
echo -e "${YELLOW}[3/4] 주문/결제 부하 테스트 실행 중...${NC}"
k6 run \
  --env BASE_URL="$BASE_URL" \
  --out json="$RESULTS_DIR/order-payment-results.json" \
  order-payment-test.js \
  > "$RESULTS_DIR/order-payment-output.txt" 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 주문/결제 테스트 완료${NC}"
else
    echo -e "${RED}✗ 주문/결제 테스트 실패${NC}"
fi
echo ""

# 대기 시간
echo -e "${BLUE}서버 리소스 복구를 위해 30초 대기...${NC}"
sleep 30
echo ""

# 3. 잔액 충전 테스트
echo -e "${YELLOW}[4/4] 잔액 충전 부하 테스트 실행 중...${NC}"
k6 run \
  --env BASE_URL="$BASE_URL" \
  --out json="$RESULTS_DIR/balance-charge-results.json" \
  balance-charge-test.js \
  > "$RESULTS_DIR/balance-charge-output.txt" 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 잔액 충전 테스트 완료${NC}"
else
    echo -e "${RED}✗ 잔액 충전 테스트 실패${NC}"
fi
echo ""

# 테스트 결과 요약
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}   테스트 결과 요약${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# 각 테스트의 주요 지표 출력
for test in coupon-issue order-payment balance-charge; do
    output_file="$RESULTS_DIR/${test}-output.txt"
    if [ -f "$output_file" ]; then
        echo -e "${GREEN}[$(echo $test | tr '-' ' ' | awk '{for(i=1;i<=NF;i++)sub(/./,toupper(substr($i,1,1)),$i)}1')]${NC}"

        # 주요 메트릭 추출 (간단 버전)
        if grep -q "http_reqs" "$output_file"; then
            echo "  - 총 요청 수: $(grep "http_reqs" "$output_file" | head -1 | grep -oE '[0-9]+' | head -1)"
        fi

        if grep -q "http_req_duration" "$output_file"; then
            echo "  - 평균 응답시간: $(grep "avg=" "$output_file" | grep "http_req_duration" | grep -oE 'avg=[0-9.]+[a-z]+' | head -1 | cut -d'=' -f2)"
        fi

        echo ""
    fi
done

echo -e "${GREEN}모든 테스트가 완료되었습니다!${NC}"
echo -e "상세 결과는 다음 경로에서 확인하세요: ${YELLOW}$RESULTS_DIR${NC}"
echo ""

# 결과 파일 목록 출력
echo -e "${BLUE}생성된 파일:${NC}"
ls -lh "$RESULTS_DIR"
echo ""

# 보고서 작성 가이드 출력
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}다음 단계: 테스트 결과 분석 및 보고서 작성${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "1. 각 테스트의 output.txt 파일에서 메트릭 확인"
echo -e "2. p95, p99 응답시간이 목표치를 달성했는지 확인"
echo -e "3. 에러율이 허용 범위 내인지 확인"
echo -e "4. 병목 지점 분석 및 개선 방안 도출"
echo -e "5. 장애 대응 문서 작성"
echo ""
echo -e "${GREEN}Good luck with your analysis! 🚀${NC}"
