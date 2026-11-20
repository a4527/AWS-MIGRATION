FROM eclipse-temurin:17-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle gradle.properties ./
COPY src src

RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

# pebble (Ubuntu service manager shipped in the base image) is not used and
# carries Go stdlib findings, so drop it from the runtime image.
RUN useradd --system --uid 10001 --create-home appuser \
    && rm -f /usr/bin/pebble

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
