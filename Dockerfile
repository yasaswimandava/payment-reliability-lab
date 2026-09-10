FROM node:24-alpine AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.15-eclipse-temurin-26 AS backend-build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./src/main/resources/static
RUN mvn --batch-mode -Dmaven.test.skip=true package \
    && cp target/payment-reliability-lab-0.0.1-SNAPSHOT.jar /workspace/application.jar

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system paymentlab \
    && useradd --system --gid paymentlab --home-dir /app --shell /usr/sbin/nologin paymentlab

WORKDIR /app
COPY --from=backend-build --chown=paymentlab:paymentlab /workspace/application.jar ./application.jar

USER paymentlab
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
