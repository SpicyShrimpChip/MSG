# 백엔드 운영 이미지. 어느 호스팅(서버·데스크탑·PaaS)이든 같은 이미지를 쓴다.
#   만들기: docker build -t msg-backend backend
#   실행 예: docker run -p 8080:8080 --env-file <운영 환경변수 파일> -v msg-uploads:/data/uploads msg-backend
# 비밀값은 이미지에 넣지 않고 실행할 때 환경변수로만 넘긴다(.env는 .dockerignore로 빠진다). 필요한 값은 docs/deploy.md 참고.
#
# 대상(target)은 두 가지다. 실행 단계는 같고 jar를 어디서 가져오는지만 다르다.
#   runtime(기본, 마지막 단계): 이미지 안에서 소스로 jar를 만든다. 로컬 docker build와 PR의 이미지 확인이 쓴다
#   prebuilt: CI가 테스트하며 이미 만든 jar(prebuilt/app.jar)를 그대로 넣는다. main 머지 때 GHCR에 올리는 이미지가 쓴다
#             (같은 소스를 이미지 안에서 한 번 더 컴파일하지 않는다, 11/25)

# ---------- jar 만들기 (runtime 대상만) ----------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 내려받기를 먼저 해서, 소스만 바뀌면 이 층을 다시 쓰게 한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src ./src
# 테스트는 CI(H2·PostgreSQL)에서 돌리므로 이미지를 만들 때는 건너뛴다
RUN ./gradlew bootJar --no-daemon -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" app.jar

# ---------- 실행 환경 (두 대상이 같이 쓴다) ----------
FROM eclipse-temurin:17-jre-jammy AS runtime-base
WORKDIR /app

# root가 아닌 계정으로 실행하고, 업로드 원본 폴더만 쓸 수 있게 한다
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /data/uploads && chown app:app /data/uploads
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_LOCAL_ROOT=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# 업로드 원본은 다시 배포해도 남도록 볼륨으로 연결한다
VOLUME ["/data/uploads"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- prebuilt: CI가 만든 jar 넣기 ----------
FROM runtime-base AS prebuilt
COPY --chown=app:app prebuilt/app.jar app.jar

# ---------- runtime(기본): 위에서 만든 jar 넣기 ----------
FROM runtime-base AS runtime
COPY --from=build --chown=app:app /workspace/app.jar app.jar
