# PWA Push Notification & User Agent Lifecycle Plan (알림 및 기기 정보 수명주기 계획서)

본 문서는 PWA 환경에서 **FCM 푸시 알림 토큰(Alarm)**과 **User Agent 기기 정보(UA)**의 프론트엔드 및 백엔드 간 생명주기 단계별 수집, 등록, 업데이트 시점을 정의합니다.

---

## 1. ALARM (FCM Web Push)

### 1.1 FCM 토큰의 규칙 및 조건
*   **권한 종속성**: FCM 토큰은 브라우저 알림 권한이 반드시 **허용(`granted`)** 상태인 경우에만 획득 가능합니다. 사용자가 권한을 거부(`denied`)하거나 아직 선택하지 않은 상태(`default`)에서는 토큰을 얻을 수 없습니다.
*   **유동성 (Volatility)**: 브라우저 캐시 삭제, 서비스 워커 업데이트, 혹은 Google FCM 서버의 만료 정책에 의해 기존 토큰이 무효화되고 새로운 토큰이 재발급될 수 있습니다.
*   **1:1 대응성**: 동일 브라우저 기기(`device_uuid`) 내에서 하나의 활성화된 디바이스 세션은 단 하나의 최신 FCM 토큰만을 가져야 합니다.

### 1.2 생명주기 단계별 FCM 등록 및 업데이트 시점

```
[ PWA 진입 ] ────────> [ 수강신청 알림 받기 설정 ] ────────> [ 로그인/기기 병합 ] ────────> [ 일상적인 앱 구동 ]
   (미발급)               (최초 권한 동의 및 등록)              (계정 매핑 전환)            (무조건 자동 최신화)
```

| 생명주기 단계 (Client Lifecycle) | FCM 토큰 상태 | 등록 및 업데이트 시점 | 상세 동작 |
| :--- | :--- | :--- | :--- |
| **PWA 최초 구동 & 게스트 가입** | 미발급 (Null) | **등록하지 않음** | 사용자가 앱 신뢰를 갖기 전이므로 알림 권한 팝업을 띄우지 않고, FCM 토큰 없이 게스트 계정을 우선 생성함 |
| **사용자의 알림 수신 설정 (동의)** | 최초 발급 | **즉시 최초 등록** | 사용자가 명시적으로 알림 설정을 켰을 때 `Notification.requestPermission()`으로 권한을 획득하고, 즉시 `getToken()`으로 FCM 토큰을 받아 백엔드 API(`POST /api/v1/notification/token`)로 등록함. 요청 시 access_token에 포함된 `deviceId`(`member_device.id`)로 현재 기기가 자동 식별됨 |
| **정식 로그인 및 게스트 병합** | 기존 토큰 유지 | **병합 API 전송 시점** | 구글 로그인 성공 후 게스트 데이터를 정식 회원 계정으로 합칠 때(`POST /api/v1/auth/login/google/merge`), 현재 기기 세션에 매핑된 FCM 토큰 정보를 신규 회원 데이터로 매핑 이전함 |
| **일상적인 앱 로드 (App Init)** | 검사 및 갱신 | **권한이 granted 이면 무조건 전송** | 앱이 켜질 때마다 브라우저 권한을 검사하여 `granted` 상태이면 즉시 `getToken()`을 호출하고 백엔드로 FCM 토큰을 **무조건 바로 전송**함 (`POST /api/v1/notification/token`). access_token의 `deviceId`로 기기가 식별되므로 별도 기기 식별자 전송 불필요. 백엔드는 이전 저장값과 동일하면 무시하여 성능을 보존함 |
| **로그아웃 (Logout)** | 토큰 파기 | **로그아웃 API 전송 시점** | 사용자가 로그아웃 시 푸시 알림 발송을 중단하기 위해, `DELETE /api/v1/notification/token`으로 현재 기기의 FCM 토큰을 백엔드 DB에서 파기함. access_token의 `deviceId`로 기기가 자동 식별됨 |

---

## 2. UA (User Agent 기기 정보)

### 2.1 UA 수집의 조건 및 파싱
*   **수집 도구**: 프론트엔드에서 `platform.js` 라이브러리를 활용해 브라우저의 raw User-Agent 문자열을 OS, 브라우저명, 버전, 기기 제조사, 모델 등의 정형화된 JSON 객체(`deviceInfo`)로 파싱하여 수집합니다.
*   **속성**: 기기의 OS나 브라우저 환경이 변경되지 않는 한 정적인 속성을 가집니다. 다만, 브라우저 업데이트가 발생하거나 다른 기기에서 접속 시 유동적으로 변동될 수 있습니다.

### 2.2 생명주기 단계별 UA 등록 및 업데이트 시점

```
[ 최초 게스트 가입 ] ────────> [ 정식 로그인 / 병합 ] ────────> [ FCM 토큰 신규 등록/갱신 ] ────────> [ 세션 연장 (Refresh) ]
   (최초 UA 정보 기록)             (정식 회원 UA 연동)              (최신 UA 정보 동봉 전송)            (주기적 UA 갱신 보정)
```

| 생명주기 단계 (Client Lifecycle) | UA 정보 상태 | 등록 및 업데이트 시점 | 상세 동작 |
| :--- | :--- | :--- | :--- |
| **최초 게스트 가입** | 최초 획득 | **게스트 생성 API 호출 시** | PWA 앱 첫 구동 시 `platform.js`로 파싱한 최신 기기 사양(`deviceInfo`)을 게스트 가입 API(`POST /api/v1/auth/guest`)의 바디에 실어서 백엔드로 즉시 전송하여 기기 세션을 생성함 |
| **정식 로그인 및 게스트 병합** | 정보 유지/이관 | **구글 로그인 / 병합 API 호출 시** | 사용자가 구글 로그인을 진행할 때 최신 기기 정보를 로그인 API의 `deviceInfo` 필드에 동봉하여 전송함. 계정이 바뀜에 따라 세션 주체의 기기 스펙 정보가 안전하게 갱신됨 |
| **FCM 토큰 신규 등록 및 갱신** | 정보 동봉 | **FCM 토큰 API 호출 시** | 알림 동의 획득이나 앱 초기화 단계에서 FCM 토큰 업데이트 API(`POST /api/v1/notification/token`)를 보낼 때, 현재 구동 중인 브라우저의 파싱된 기기 정보(`deviceInfo`)를 함께 탑승시켜 전송함 |
| **세션 연장 (Access Token Refresh)** | 주기적 보정 | **Refresh API 호출 시** | Access Token 만료로 인해 재발급 API(`POST /api/v1/auth/refresh`)를 호출할 때 최신 UA 정보를 헤더나 파라미터로 동봉함. 사용자가 브라우저 자동 업데이트 등을 거쳤을 때의 미세한 버전을 세션 연장 시점에 실시간 보정하는 용도로 작동함 |



표준 notification 형식
```
{
  "token": "fcm_device_token_sample",
  "notification": {
    "title": "공지 알림",
    "body": "새로운 공지가 등록되었습니다. 앱을 확인하세요!"
  },
  "data": {
    "type": "GENERAL",
    "path": "/",
    "timestamp": 1710000000000
  }
}
```

type : 
GENERAL(/)
MULTIGAME_RESERVED_TIME(/multigame)
EXCHANGE_MATCHED(/exchange/rooms/{roomId})
EXCHANGE_NEW_MESSAGE(/exchange/rooms/{roomId})

data 에는 type, path, timestamp 를 넣어주고, path 는 앱에서 해당 경로로 이동할 수 있도록 해준다.

notification consumer 계층은 그냥 보내는 것만 담당하므로 여기서 data 객체에 대한 validation 은 하지 않는다. 각 도메인에서 적절한 세팅을 할 뿐이다