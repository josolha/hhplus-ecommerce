# 환경 변수 설정 가이드

## 로컬 개발 환경 설정

### 1. application.yml 파일 생성

`src/main/resources/application.yml.example` 파일을 복사하여 `application.yml`로 이름 변경:

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

### 2. 환경 변수 설정

다음 환경 변수들을 설정해야 합니다:

#### macOS/Linux
```bash
export DB_URL="jdbc:mysql://localhost:3306/ecommerce?serverTimezone=Asia/Seoul"
export DB_USER="root"
export DB_PASSWORD="your_password_here"
```

#### Windows (PowerShell)
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/ecommerce?serverTimezone=Asia/Seoul"
$env:DB_USER="root"
$env:DB_PASSWORD="your_password_here"
```

#### IntelliJ IDEA 설정
1. Run > Edit Configurations
2. Environment variables 항목에 추가:
   ```
   DB_URL=jdbc:mysql://localhost:3306/ecommerce?serverTimezone=Asia/Seoul;DB_USER=root;DB_PASSWORD=your_password_here
   ```

### 3. 필수 환경 변수

| 변수명 | 설명 | 기본값 | 필수 여부 |
|--------|------|--------|----------|
| DB_URL | 데이터베이스 연결 URL | jdbc:mysql://localhost:3306/ecommerce?serverTimezone=Asia/Seoul | 선택 |
| DB_USER | 데이터베이스 사용자명 | root | 선택 |
| DB_PASSWORD | 데이터베이스 비밀번호 | (없음) | **필수** |

### 4. 주의사항

- `application.yml`, `application-dev.yml`, `application-prod.yml` 파일들은 `.gitignore`에 포함되어 있습니다
- 절대로 실제 비밀번호를 Git에 커밋하지 마세요
- 팀원들과 공유할 때는 이 가이드 문서를 참고하도록 안내하세요

### 5. 프로필별 설정

#### local (기본)
- `application.yml` 사용
- `ddl-auto: create` - 매번 스키마 재생성

#### dev
- `application-dev.yml` 사용
- `ddl-auto: update` - 스키마 업데이트

#### prod
- `application-prod.yml` 사용
- `ddl-auto: validate` - 검증만 수행

프로필 변경:
```bash
# dev 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# prod 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=prod'
```
