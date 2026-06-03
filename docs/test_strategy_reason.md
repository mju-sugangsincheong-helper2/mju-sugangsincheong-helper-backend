Strategy Rationale

이 문서는 프로젝트의 테스트 전략과 각 테스트 유형의 도입 의도를 설명합니다.

---

## 1. 테스트 계층 구조

```
┌─────────────────────────────────────────────────────────┐
│  통합 테스트 (Integration Test)                          │
│  - @SpringBootTest + @AutoConfigureMockMvc              │
│  - 실제 DB, 실제 Bean 사용                               │
│  - 계층 간 연동 검증                                     │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│  슬라이스 테스트 (Slice Test)                            │
│  - @WebMvcTest                                          │
│  - 특정 계층만 격리                                      │
│  - Mock으로 의존성 대체                                  │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│  단위 테스트 (Unit Test)                                 │
│  - @ExtendWith(MockitoExtension.class)                  │
│  - 순수 Java 로직                                        │
│  - Spring 컨텍스트 없음                                   │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 테스트 유형별 특징

### 2.1 단위 테스트 (Unit Test)

| 특징 | 설명 |
|------|------|
| **속도** | 가장 빠름 (밀리초 단위) |
| **격리** | 완전 격리, Spring 컨텍스트 없음 |
| **도구** | JUnit 5, Mockito |
| **대상** | 비즈니스 로직, 도메인 메서드 |

**도입 의도**:
- 비즈니스 로직의 정확성을 빠르게 검증
- 리팩토링 시 회귀 테스트로 활용
- TDD 사이클에 적합

```java
@ExtendWith(MockitoExtension.class)
class ExampleServiceTest {

    @InjectMocks ExampleService service;
    
    @Test
    void create_shouldSaveAndReturn() {
        // Given
        ExampleCreateRequest request = ExampleCreateRequest.builder()
                .title("Test")
                .content("Content")
                .build();
        given(repository.save(any())).willReturn(entity);
        
        // When
        ExampleDetailResponse response = service.create(request);
        
        // Then
        assertThat(response.getTitle()).isEqualTo("Test");
    }
}
```

### 2.2 슬라이스 테스트 (Slice Test)

| 특징 | 설명 |
|------|------|
| **속도** | 중간 (초 단위) |
| **격리** | 특정 계층만 로드 |
| **도구** | `@WebMvcTest`, MockMvc |
| **대상** | Controller, Filter, ExceptionHandler |

**도입 의도**:
- HTTP 요청/응답 검증
- 요청 검증 (@Valid) 동작 확인
- Filter, Interceptor 동작 확인
- Security 설정 확인

```java
@WebMvcTest(ExampleController.class)
@Import({GlobalExceptionHandler.class, GlobalMetaFilter.class, ClientInfoExtractor.class})
@WithMockUser
class ExampleControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ExampleService service;
    
    @Test
    void hello_shouldReturn200() throws Exception {
        given(service.hello("world")).willReturn(response);
        
        mockMvc.perform(get("/api/v1/example/hello")
                        .param("name", "world"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("hello world"));
    }
}
```

**주의사항**:
- `@WebMvcTest`는 Controller만 로드하므로 필요한 Bean을 `@Import`로 추가
- Security가 활성화된 경우 `@WithMockUser` 또는 `csrf()` 필요
- Spring Boot 4.x에서는 패키지 변경: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`

### 2.3 통합 테스트 (Integration Test)

| 특징 | 설명 |
|------|------|
| **속도** | 가장 느림 (수초~수십초) |
| **격리** | 전체 시스템 |
| **도구** | `@SpringBootTest`, 실제 DB |
| **대상** | End-to-End 흐름 |

**도입 의도**:
- 계층 간 연동 검증
- 실제 DB 쿼리 동작 확인
- 트랜잭션 동작 확인
- 전체 시스템 동작 검증

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExampleIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ExampleRepository repository;
    
    @Test
    void createAndFind_shouldWorkEndToEnd() throws Exception {
        // Create
        mockMvc.perform(post("/api/v1/example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());
        
        // Verify in DB
        assertThat(repository.findAll()).hasSize(1);
    }
}
```

**전제조건**:
 - PostgreSQL 테스트 DB 필요: `mjusugangsincheonghelperdb`
- 테스트 DB 생성: `createdb mjusugangsincheonghelperdb`

---

## 3. 테스트 패키지 구조

```
src/test/java/com/mjusugangsincheonghelper/
├── database/
│   ├── entity/
│   │   └── ExampleEntityTest.java          (단위)
│   └── repository/
│       └── ExampleRepositoryTest.java      (통합)
└── example/
    ├── controller/
    │   └── ExampleControllerTest.java      (슬라이스)
    ├── service/
    │   └── ExampleServiceTest.java     (단위)
    └── ExampleIntegrationTest.java         (통합)
```

---

## 4. 테스트 작성 규칙

### 4.1 Given-When-Then 패턴

```java
@Test
void create_shouldSaveAndReturn() {
    // Given (준비)
    ExampleCreateRequest request = ExampleCreateRequest.builder()
            .title("Test")
            .build();
    given(repository.save(any())).willReturn(savedEntity);
    
    // When (실행)
    ExampleDetailResponse response = service.create(request);
    
    // Then (검증)
    assertThat(response.getTitle()).isEqualTo("Test");
    verify(repository).save(any());
}
```

### 4.2 중첩 클래스로 테스트 그룹화

```java
@Nested
@DisplayName("create 메서드는")
class Describe_create {
    
    @Test
    @DisplayName("요청을 받아 엔티티를 저장한다")
    void it_saves_entity() { ... }
    
    @Test
    @DisplayName("유효하지 않은 요청이면 예외를 발생시킨다")
    void it_throws_exception_when_invalid() { ... }
}
```

### 4.3 BDD 스타일 Mocking

```java
// Given (BDD 스타일)
given(repository.findById(1L)).willReturn(Optional.of(entity));

// When
service.findById(1L);

// Then (BDD 스타일)
verify(repository).findById(1L);
```

---

## 5. 테스트 실행 명령

### 단위 테스트만 실행
```bash
./gradlew test --tests "com.mjusugangsincheonghelper.example.service.*" \
               --tests "com.mjusugangsincheonghelper.database.entity.*"
```

### 슬라이스 테스트만 실행
```bash
./gradlew test --tests "com.mjusugangsincheonghelper.example.controller.*"
```

### 통합 테스트 실행 (DB 필요)
```bash
# 먼저 테스트 DB 생성
createdb mjusugangsincheonghelperdb

# 테스트 실행
./gradlew test --tests "com.mjusugangsincheonghelper.example.ExampleIntegrationTest" \
               --tests "com.mjusugangsincheonghelper.database.repository.*"
```

### 전체 테스트 실행
```bash
./gradlew test
```

---

## 6. Spring Boot 4.x 테스트 변경사항

### 패키지 변경

| Spring Boot 3.x | Spring Boot 4.x |
|-----------------|-----------------|
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` | **제거됨** |

### @DataJpaTest 제거

Spring Boot 4.x에서는 `@DataJpaTest`가 제거되었습니다. Repository 테스트는 `@SpringBootTest`를 사용합니다:

```java
@SpringBootTest
@Transactional
class ExampleRepositoryTest {
    @Autowired ExampleRepository repository;
    
    @Test
    void save_shouldAssignId() {
        ExampleEntity entity = ExampleEntity.builder()
                .title("Test")
                .build();
        
        ExampleEntity saved = repository.save(entity);
        
        assertThat(saved.getId()).isNotNull();
    }
}
```

---

## 7. 테스트 커버리지 목표

| 계층 | 목표 | 비고 |
|------|------|------|
| Service | 90%+ | 비즈니스 로직 핵심 |
| Controller | 80%+ | HTTP 응답, 검증 |
| Repository | 70%+ | 커스텀 쿼리 위주 |
| Entity | 80%+ | 도메인 메서드 |
| Global | 60%+ | 유틸리티, 예외 처리 |

---

## 8. 테스트 우선순위

### Phase 1: 핵심 비즈니스 로직 (단위)
1. `ExampleServiceTest` - CRUD, 예외 발생
2. `ExampleEntityTest` - 도메인 메서드

### Phase 2: API 계층 (슬라이스)
3. `ExampleControllerTest` - HTTP 응답, 검증, Security

### Phase 3: 통합 검증 (통합)
4. `ExampleRepositoryTest` - DB 쿼리 동작
5. `ExampleIntegrationTest` - 전체 흐름

---

## 9. 새 도메인 테스트 체크리스트

1. **Entity 테스트** (단위)
   - 생성자 동작 확인
   - 비즈니스 메서드 확인
   - 상태 변경 확인

2. **Repository 테스트** (통합)
   - CRUD 동작 확인
   - 커스텀 쿼리 확인
   - 페이징 확인

3. **Service 테스트** (단위)
   - 비즈니스 로직 확인
   - 예외 발생 확인
   - 트랜잭션 경계 확인

4. **Controller 테스트** (슬라이스)
   - HTTP 상태코드 확인
   - 요청 검증 확인
   - 응답 구조 확인
   - Security 확인

5. **통합 테스트** (통합)
   - End-to-End 흐름 확인
   - 응답 메타데이터 확인

---

## 10. 설계 원칙 요약

| 원칙 | 적용 |
|------|------|
| **빠른 피드백** | 단위 테스트 우선, 통합 테스트는 CI에서 |
| **격리** | 각 테스트는 독립적, 순서 무관 |
| **가독성** | Given-When-Then, 중첩 클래스, DisplayName |
| **유지보수성** | 테스트도 리팩토링, 중복 제거 |
| **신뢰성** | 실제 환경과 유사한 통합 테스트 |
