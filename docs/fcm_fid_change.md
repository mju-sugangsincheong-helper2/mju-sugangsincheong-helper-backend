# FCM Registration Token -> Firebase Installation ID (FID) 마이그레이션 기술 명세서

본 문서는 Google Firebase의 FCM 메시징 식별자 마이그레이션 방침에 따라 기존 **FCM 토큰(Registration Token)** 방식에서 **Firebase Installation ID (FID)** 방식으로 전환함에 있어 배경, 개발자 고민점, 백엔드 및 클라이언트 변경점을 정리한 기술 가이드입니다.

---

## 1. 마이그레이션 배경 (Overview)

Google Firebase 팀은 최신 Firebase Admin SDK(v9.10.0 이상) 및 FCM v1 API 표준 규격에서 기존의 `Registration Token` 사용을 비권장(Deprecated) 처리하고, **Firebase Installation ID (FID)**를 앱 인스턴스의 공식 표준 식별자로 지정하였습니다.

- **기존 방식:** `Message.builder().setToken(token)` (Deprecated)
- **변경 방식:** `Message.builder().setFid(fid)` (Recommended Standard)

---

## 2. 구글 공식 사양 및 변경 사항 (Specifications)

### 2.1. Firebase Admin SDK (Java)
- `Message.Builder.setToken(String)` 메서드가 `@Deprecated` 처리되었습니다.
- 단일 타겟 발송 시 **`Message.Builder.setFid(String fid)`** 사용이 강제/권장됩니다.
- 다중 멀티캐스트 발송 시 `MulticastMessage.Builder.addFid(String fid)` / `addAllFids(Collection<String> fids)` 사용을 권장합니다.

### 2.2. 전환 기간 (Transition Period)
- 마이그레이션 유예 기간 동안은 기존 `token` 필드도 내부적으로 FID 및 토큰을 하위 호환 형태로 수용하지만, 장기적 호환성과 경고 제거를 위해 **FID 기반 메서드로 전면 전환**해야 합니다.

---

## 3. 개발자 입장에서의 고민점 (Developer Considerations)

### 3.1. 토큰 갱신(로테이션)으로 인한 탈착 문제 해소
- **기존 FCM 토큰의 한계:** 네트워크 환경 변화, 앱 업데이트, 주기적 보안 로테이션(약 270일) 등으로 예고 없이 토큰이 바뀌어 백엔드 DB와의 동기화가 깨지고 알림이 유실되는 문제가 빈번했습니다.
- **FID의 장점:** 앱을 삭제하거나 브라우저 스토리지를 완전 초기화하지 않는 한 **FID는 영구히 고정**되므로, 기기 식별 및 동기화 무결성이 획기적으로 향상됩니다.

### 3.2. 유저 - 기기 1:N 바인딩 추적의 용이성
- 한 사용자가 여러 기기(스마트폰, 태블릿, PC 브라우저)를 보유하거나 한 기기에서 계정이 전환(Login/Logout)되는 상황에서, 변하지 않는 FID를 DB Key로 관리함으로써 안정적인 알림 타겟팅이 가능합니다.

### 3.3. 하위 호환성 및 마이그레이션 리스크
- 기존 FCM 토큰을 저장하고 있던 레거시 클라이언트 사용자를 위해 백엔드 DB 컬럼 구조를 명확히 정립하고, 전환 기간 동안 유연하게 수용할 수 있는 API 설계가 필요합니다.

---

## 4. 백엔드(Backend) 시스템 변경점

### 4.1. 데이터베이스 스키마 (Database Schema)
- `member_device` 테이블의 컬럼 명칭을 `firebase_installation_id`로 명시적 변경했습니다.

```sql
ALTER TABLE member_device RENAME COLUMN fcm_token TO firebase_installation_id;
```

- JPA Entity ([`MemberDevice.java`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/database/entity/MemberDevice.java)):
  ```java
  @Column(name = "firebase_installation_id", length = 512)
  private String fid;
  ```

### 4.2. Firebase Admin SDK 전송 서비스 ([`NotificationConsumerService.java`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/notification/consumer/service/NotificationConsumerService.java))
- Deprecated된 `setToken` 대신 최신 **`setFid(event.getFid())`** 메서드로 전환하여 컴파일 경고를 근본적으로 제거했습니다.

```java
Message message = Message.builder()
    .setFid(event.getFid()) // setToken() 대신 setFid() 적용
    .setNotification(Notification.builder()
        .setTitle(event.getNotification().getTitle())
        .setBody(event.getNotification().getBody())
        .build())
    .putAllData(event.getData())
    .build();
```

### 4.3. API DTO 및 컨트롤러 규격 ([`NotificationController.java`](file:///Users/shinnk/source/project/mju-sugangsincheong-heler/mju-sugangsincheong-helper-backend/src/main/java/com/mjusugangsincheonghelper/notification/controller/NotificationController.java))
- DTO 필드명을 `fcmToken`에서 **`fid`**로 변경하였습니다.
- `POST /api/v1/notification/token` (FID 등록/갱신)
- `DELETE /api/v1/notification/token` (FID 삭제)

```json
// Request Body
{
  "fid": "d-xxxxxx-xxxx-xxxx"
}
```

---

## 5. 클라이언트(Client - Web / Mobile) 변경점

클라이언트 애플리케이션은 Firebase SDK 초기화 후 FCM 토큰 대신 **FID (Firebase Installation ID)**를 발급받아 백엔드 API로 전달해야 합니다.

### 5.1. Web 브라우저 (JS / React / Next.js)

```javascript
import { initializeApp } from "firebase/app";
import { getInstallations, getId } from "firebase/installations";

const app = initializeApp(firebaseConfig);
const installations = getInstallations(app);

// 1. FID 발급
const fid = await getId(installations);

// 2. 백엔드로 FID 등록 요청
await fetch('/api/v1/notification/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ fid: fid })
});
```

### 5.2. Android (Kotlin)

```kotlin
FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        val fid = task.result
        // 백엔드 API로 fid 전송
    }
}
```

### 5.3. iOS (Swift)

```swift
Installations.installations().installationID { (id, error) in
    if let fid = id {
        // 백엔드 API로 fid 전송
    }
}
```

---

## 6. 결론 및 기대 효과 (Conclusion & Benefits)

1. **컴파일 경고 제거 및 최신 표준 준수:** Firebase Admin SDK 9.10.0+ 호환성 확보 및 `setFid()` 적용으로 코드 품질 향상.
2. **알림 도달율 개선:** 수시로 변경되는 토큰 불일치 문제 해소로 푸시 알림 도달율 극대화.
3. **유지보수 용이성:** DB 컬럼(`firebase_installation_id`) 명시화를 통한 도메인 모델 직관성 향상.
