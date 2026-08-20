# script/pg

PostgreSQL 운영 **조회 전용** 스크립트.

## 반드시 지키는 것

1. 프로젝트 루트의 기존 `.env`를 그대로 사용한다.
2. `.env`를 `source`하지 않는다.
3. 추가 환경변수를 요구하지 않는다.
4. `docker compose`를 호출하지 않는다.
5. `db`라는 컨테이너 이름을 하드코딩하지 않는다.
6. `postgres:*` 이미지를 하드코딩하지 않는다.
7. 실제 실행 중인 컨테이너에 `psql`이 존재하고 `.env`의 DB 계정으로 실제 접속되는지 검사해서 PostgreSQL 컨테이너를 찾는다.
8. 조회 스크립트에서는 `VACUUM`, `ANALYZE`, `DELETE`, `UPDATE`, `ALTER` 등을 실행하지 않는다.

## 실행

```bash
chmod +x script/pg/*.sh
bash script/pg/health.sh
bash script/pg/all.sh
```
