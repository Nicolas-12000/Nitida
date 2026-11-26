# syntax=docker/dockerfile:1
# Build stage: full JDK, Gradle with dependency verification enforced.
FROM eclipse-temurin:21-jdk@sha256:db5cc6341a1d7bc40cdb51e1f0d9824f3bb2cc8107d29da421dc4df7d11f05f1 AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Separate layer: dependencies are downloaded (and verified) only when the build files change, not on every code edit.
RUN ./gradlew dependencies --no-daemon > /dev/null
COPY src src
RUN ./gradlew bootJar -x test --no-daemon \
 && mkdir -p /data/files

# Runtime stage: distroless (no shell, no package manager), non-root.
# Debian 13, not 12: the published debian12 image still ships brotli 1.0.9-2 (CVE-2026-111497).
# This digest has libbrotli1 1.1.0-2+deb13u1.
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:128bd878463537eef7771b50fecf68faace9089f654a2d233f1c4f344e6817d9
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
COPY --from=build --chown=65532:65532 /data /data
ENV NITIDA_FILES_ROOT=/data/files
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
