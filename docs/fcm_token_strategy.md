기본적으로 fcm 토큰을 관리하는 도메인명은 notification 이라고 한다

notification 도메인 
1. fcm 토큰 관리
2. 다른 도메인에서 pgmq 를 통해 발생된 이벤트(알림 이벤트가 이벤트 큐에 들어오면 notification 도메인에서 처리) 처리, 알림 권한 요청, 알림 수신 시 처리 등과 관련된 전략을 정의한다. 여기서는 이벤트 큐에서 들어온 것을 단순하게 400 개 단위로만 잘라서 처리한다




fcm 토큰 라이프 사이클
```
[ 앱 켜짐 / 로그인 상태 체크 ]
   │
   ├── 1. 비로그인 상태
   │      └── FCM 관련 로직 실행 안 함 (대기)
   │
   └── 2. 로그인 상태
          ├── A. 이미 권한 허용됨 (Notification.permission === 'granted')
          │      └── [앱 켜질 때 자동 갱신] getToken() ──> 변경/만료 시 백엔드 동기화
          │
          └── B. 권한 미정/거부됨 (default / denied)
                 └── 자동 갱신 안 함
                 └── 서비스 내 지정된 시점(버튼 클릭, 특정 기능 진입 등)에 "소프트 권한 안내" 노출
                        └── 유저가 [알림 받기] 클릭 ──> Notification.requestPermission()
                               └── [허용] 클릭 ──> getToken() ──> 백엔드 동기화
[ 2. 로그아웃 (토큰 정리) ]
   │
   ├── 2-1. 백엔드 요청: DELETE xxx
         └── 현재 기기의 Firebase Cloud Messaging 토큰을 백엔드로 전달하여 DB에서 삭제

```


pgmq 이벤트 큐에서 들어온 알림 이벤트 처리 전략
누구에게 알림을 보낼지등은 해당 도메인에서 모두 알고 있다고 가정하고 notification 도메인에서는 단순하게 이벤트 큐에서 들어온 알림 이벤트를 400개 단위로 잘라서 처리한다.

여기서는 fcm crud api 가 존재하며 controller, service, repository 3개가 기본적으로 존재
즉 여기는 특이하게 consumer 패키지가 존재
consumer 패키지에서는 pgmq 이벤트 큐에서 들어온 알림 이벤트를 400개 단위로 잘라서 처리한다
보내는 측에서는 데이터 구조를 보내는 데이터 구조에 그대로 동일하게 맞추어서 이벤트를 쌓아야 한다
```
{
    token: .,
    notification: {
      title: "공지 알림",
      body: "새로운 공지가 등록되었습니다. 앱을 확인하세요!"
    },
    data: {
      type: "NOTICE",
      urgency: "NORMAL",
      timestamp: $ts
    }
}
```

9.10.0 firebase admin sdk 추가하자



### 4.2. Consumer 패키지 구조 및 처리 로직

Consumer 패키지는 `com.mjusugangsincheonghelper.notification.consumer` 하위에 구성되어 있습니다.

- **`NotificationConsumerWorker`**:
  - PGMQ의 `notification_queue`를 1초 단위로 폴링합니다.
  - 가시성 타임아웃 30초, 배치 사이즈 최대 400개 단위로 읽어옵니다.
  - 재시도 횟수(`readCt`)가 5회를 초과하는 독약 메시지(Poison Message)는 `pgmqService.archive()`로 보관 처리합니다.
  - 메시지 수신 후 역직렬화하여 `NotificationConsumerService`로 전달하고, 정상 처리 성공 시 `pgmqService.delete()`로 삭제합니다.
  - 처리 중 예외 발생 시 메시지를 삭제하지 않으므로 가시성 타임아웃 이후 PGMQ에 의해 자동 재시도됩니다.

- **`NotificationConsumerService`**:
  - `processNotificationEvents(List<NotificationEventMessage> events)`
  - 수신된 이벤트 메시지 리스트를 최대 **400개 단위(Batch Size)**로 파티셔닝(SubList)하여 FCM으로 배치 전송을 수행합니다.
  - `FirebaseMessaging.getInstance().sendEach(messages)`를 호출하여 구글 Firebase Cloud Messaging 서버로 멀티캐스트/배치 전송합니다.

- **`NotificationEventMessage`**:
  - `token`: String (수신 대상 Firebase Cloud Messaging 토큰)
  - `notification`: `title`, `body`
  - `data`: `Map<String, String>` (`type`, `urgency`, `timestamp` 등)
  - *원칙*: **Notification Consumer는 `data` 내부 값을 검증하거나 관여하지 않으며(Decoupled), 이벤트를 발행하는 각 도메인(Producer)에서 아래 규약에 맞춰 큐에 전달해야 합니다.**

- **[발행 도메인(Producer) 작성 규약] 이벤트 `type` 및 `urgency` 정의**:

  | `type` | `urgency` | 설명 |
  | :--- | :--- | :--- |
  | `SYSTEM_NOTICE` | `NORMAL` | 시스템/일반 공지사항 알림 |
  | `SYSTEM_MAINTENANCE` | `HIGH` | 시스템 정기/긴급 점검 안내 알림 |
  | `EXCHANGE_MATCH_SUCCESS` | `HIGH` | 강의 교환 매칭 성공 알림 |
  | `EXCHANGE_MESSAGE_RECEIVED` | `NORMAL` | 교환방 내 신규 채팅 메시지 수신 알림 |
  | `MULTIGAME_RESERVATION_REMINDER` | `HIGH` | 모의 수강신청 게임 예약 시각 알림 |

---

### 4.3. 테스트 및 검증 가이드

#### 1) PGMQ 큐에 직접 알림 메시지 투입 (SQL)

PostgreSQL 콘솔 또는 DBeaver 등에서 아래 SQL로 알림 이벤트를 발행하면 PGMQ Consumer가 1초 내로 메시지를 읽어 FCM으로 발송합니다.

```sql
SELECT pgmq.send(
    'notification_queue',
    '{
        "token": "실제_FCM_토큰_입력",
        "notification": {
            "title": "공지 알림",
            "body": "새로운 공지가 등록되었습니다."
        },
        "data": {
            "type": "SYSTEM_NOTICE",
            "urgency": "NORMAL",
            "timestamp": 1700000000
        }
    }'::jsonb
);
```

#### 2) 웹 푸시 실제 수신 테스트 페이지
- **테스트 URL**: `http://localhost:8080/fcm-test.html`
- 접속 후 **[알림 권한 허용 및 Firebase Cloud Messaging 토큰 발급]** 클릭 시 브라우저용 실시간 Firebase Cloud Messaging 토큰 발급.
- 발급받은 토큰으로 쿼리 실행 후 브라우저 탭을 비활성화(다른 탭 이동)하면 PC 화면 우측 하단에 실제 푸시 알림 팝업이 노출됩니다.




