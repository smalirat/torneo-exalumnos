# syntax=docker/dockerfile:1
# Backend (Spring Boot). Two stages: Maven builds the jar, a small JRE image runs it.

# ---- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first: this layer is reused while pom.xml does not change
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package \
    && cp target/*.jar /app.jar

# ---- run
FROM eclipse-temurin:21-jre-alpine
# Never run as root inside the container
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8081
# Use up to 75% of the container's memory limit for the heap
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8081/api/public/league > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
