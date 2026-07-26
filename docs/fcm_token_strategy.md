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
         └── 현재 기기의 FCM 토큰을 백엔드로 전달하여 DB에서 삭제

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



