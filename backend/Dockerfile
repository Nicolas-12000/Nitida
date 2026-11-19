# syntax=docker/dockerfile:1
# Build stage: full JDK, Gradle with dependency verification enforced.
FROM eclipse-temurin:24-jdk@sha256:7493205ffe6caa8074fa8a06a276bb1c5ac41d3dd0fd43a0db66d7f776e80b3e AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN ./gradlew bootJar -x test --no-daemon \
 && mkdir -p /data/files

# Runtime stage: distroless (no shell, no package manager), non-root.
FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d
WORKDIR /app
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
COPY --from=build --chown=65532:65532 /data /data
ENV NITIDA_FILES_ROOT=/data/files
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
