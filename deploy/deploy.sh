#!/usr/bin/env bash
# 서버의 백엔드를 최신 이미지로 바꾼다: 이미지 받기 → 다시 띄우기 → 응답 확인.
# main에 백엔드 변경이 머지되고 CI가 이미지를 올린 뒤 서버에서 실행한다: ./deploy.sh
# DB 데이터·업로드 파일·인증서는 볼륨에 있어 그대로 남는다.
set -euo pipefail

cd "$(dirname "$0")"

docker compose pull
docker compose up -d
docker image prune -f > /dev/null

# 공개 API(공지 목록)가 응답할 때까지 최대 2분 기다린다. 처음 띄울 때는 Flyway가 테이블을 만드느라 조금 더 걸린다
for _ in $(seq 1 24); do
	if docker compose exec -T caddy wget -q -O /dev/null http://backend:8080/api/notices; then
		echo "백엔드가 응답해요."
		docker compose ps
		exit 0
	fi
	sleep 5
done

echo "백엔드가 2분 안에 응답하지 않았어요. 로그를 확인해 주세요: docker compose logs --tail=100 backend" >&2
exit 1
