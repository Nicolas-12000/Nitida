# syntax=docker/dockerfile:1
# Build stage: full JDK, Gradle with dependency verification enforced.
FROM eclipse-temurin:25-jdk@sha256:ce2ef9d2d67a68de5e196d4de9d743327c44a10b57fe02ec39f572c652cacfd3 AS build
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
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:cddca4cc6b1dfc76142e5c1a1d00ce7a79bcc1292ad65d34227794f9d371a496
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
COPY --from=build --chown=65532:65532 /data /data
ENV NITIDA_FILES_ROOT=/data/files
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
