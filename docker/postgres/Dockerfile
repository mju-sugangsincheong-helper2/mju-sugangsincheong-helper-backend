# docker/postgres/Dockerfile
FROM postgres:17
USER root
ENV DEBIAN_FRONTEND=noninteractive

# 1) pg_cron 및 system_stats 빌드를 위한 컴파일러 패키지 설치
RUN apt-get update && apt-get install -y --no-install-recommends \
    postgresql-17-cron \
    postgresql-server-dev-17 \
    make \
    gcc \
    git \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 2) pgmq 빌드 및 설치
RUN git clone https://github.com/pgmq/pgmq.git /tmp/pgmq \
    && cd /tmp/pgmq/pgmq-extension \
    && make install \
    && rm -rf /tmp/pgmq

# 3) system_stats 빌드 및 설치
RUN git clone https://github.com/EnterpriseDB/system_stats.git /tmp/system_stats \
    && cd /tmp/system_stats \
    && PATH=/usr/lib/postgresql/17/bin:$PATH make \
    && PATH=/usr/lib/postgresql/17/bin:$PATH make install \
    && rm -rf /tmp/system_stats

# 4) 빌드 도구 정리 (컨테이너 경량화)
RUN apt-get purge -y --auto-remove git make gcc postgresql-server-dev-17 \
    && rm -rf /var/lib/apt/lists/*

# 5) pg_cron 설정
RUN echo "shared_preload_libraries = 'pg_cron'" >> /usr/share/postgresql/postgresql.conf.sample \
    && echo "cron.database_name = 'mjusugangsincheonghelperdb_dev'" >> /usr/share/postgresql/postgresql.conf.sample

USER postgres
