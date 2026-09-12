# IMS 应用镜像：多阶段构建，运行时仅保留 JRE + fat jar
# 不使用 # syntax=docker/dockerfile:1，避免构建时再拉 docker/dockerfile 前端镜像
#
# 国内拉不到 Docker Hub 时，构建参数 DOCKER_HUB=docker.m.daocloud.io/library

ARG DOCKER_HUB=docker.io/library
FROM ${DOCKER_HUB}/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 走阿里云 Maven 镜像，避免国内拉取中央仓库超时
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package \
    && cp target/inventory-0.0.1-SNAPSHOT.jar /build/app.jar

ARG DOCKER_HUB=docker.io/library
FROM ${DOCKER_HUB}/eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system ims \
    && useradd --system --gid ims --home-dir /app --no-create-home ims

COPY --from=build /build/app.jar /app/app.jar
RUN chown ims:ims /app/app.jar

USER ims

ENV TZ=UTC \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/login.html >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
