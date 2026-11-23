#!/usr/bin/env bash
# DB와 업로드한 학습 자료 파일을 deploy/backups/에 남기고, KEEP_DAYS(기본 7일)가 지난 백업은 지운다.
# 서버에서 매일 실행한다(crontab -e):
#   0 4 * * * /home/ubuntu/MSG/deploy/backup.sh >> /home/ubuntu/msg-backup.log 2>&1
# 백업에도 개인정보가 있다. 서버 밖으로 옮기지 않고, 탈퇴한 사용자의 정보도 KEEP_DAYS 뒤 백업과 함께 지워진다(처리방침 5번).
# 복원 방법은 docs/deploy.md "백업과 복원".
set -euo pipefail

cd "$(dirname "$0")"
umask 077
KEEP_DAYS="${KEEP_DAYS:-7}"
mkdir -p backups

stamp="$(date +%F-%H%M)"
docker compose exec -T db pg_dump -U postgres --format=custom ai_lms > "backups/db-$stamp.dump"
docker compose exec -T backend tar -C /data/uploads -czf - . > "backups/uploads-$stamp.tar.gz"

find backups -type f -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') 백업 완료: db-$stamp.dump, uploads-$stamp.tar.gz"
