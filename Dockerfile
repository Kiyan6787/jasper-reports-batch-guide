# syntax=docker/dockerfile:1

# ---- Build: compiles the JRXML and Java, produces the fat JAR ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# Cache ~/.m2 between builds so dependencies are downloaded only once.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package

# ---- Runtime ----
FROM eclipse-temurin:21-jre

# AWT measures text with FreeType via fontconfig even when headless.
# Slim images without it fail at fill time with font or native-library errors.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --home-dir /app app \
    && mkdir -p /app/out \
    && chown -R 10001 /app
WORKDIR /app
COPY --from=build /src/target/batch_guide-1.0-SNAPSHOT.jar /app/app.jar

USER 10001
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
