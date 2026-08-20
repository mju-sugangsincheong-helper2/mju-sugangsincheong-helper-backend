# script/pg

프로젝트 루트의 `.env`를 그대로 사용한다.

중요: `.env`를 `source`하지 않는다. 현재 `.env`의 Firebase private key는
`-----BEGIN PRIVATE KEY-----`처럼 Bash assignment에 안전하지 않은 공백을
포함하므로, 필요한 DB_* 값만 dotenv 방식으로 읽는다.

DB 연결 정보:
- DB_URL
- DB_USERNAME
- DB_PASSWORD

DB_URL의 host를 Docker Compose service 이름으로 사용한다.
예:
DB_URL=jdbc:postgresql://db:5432/mjusugangsincheonghelperdb_prod
=> docker compose exec -T db psql ...

따라서 컨테이너 이름을 하드코딩하지 않는다.

실행:
  bash script/pg/health.sh
  bash script/pg/all.sh

모든 스크립트는 운영 조회용이며 데이터 변경 SQL을 수행하지 않는다.
