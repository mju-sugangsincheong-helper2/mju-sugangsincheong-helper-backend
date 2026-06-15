FROM postgres:17

USER root

ENV DEBIAN_FRONTEND=noninteractive

# 1) pg_cron 및 pgmq 빌드를 위한 패키지 설치
RUN apt-get update && apt-get install -y --no-install-recommends \
    postgresql-17-cron \
    postgresql-server-dev-17 \
    make \
    git \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 2) 공식 pgmq 최신 저장소 복제 후 설치 실행
RUN git clone https://github.com/pgmq/pgmq.git /tmp/pgmq \
    && cd /tmp/pgmq/pgmq-extension \
    && make install \
    && rm -rf /tmp/pgmq

# 3) 빌드 도구 정리 (컨테이너 경량화)
RUN apt-get purge -y --auto-remove git make postgresql-server-dev-17 \
    && rm -rf /var/lib/apt/lists/*

# 볼륨 권한 문제를 해결하기 위해 엔트리포인트를 root 계정으로 유지합니다.
# (내부 스크립트가 로컬 볼륨 권한 복구 후 DB 데몬은 안전하게 postgres 유저로 실행합니다.)
USER root
