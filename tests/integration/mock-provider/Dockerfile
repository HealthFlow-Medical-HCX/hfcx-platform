# Multi-stage build keeps the runtime image slim and avoids shipping Maven
# into the harness image. Java 11 runtime matches the hcx-apis target.
FROM maven:3.8.6-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /build/target/mock-provider.jar /app/mock-provider.jar
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
    CMD wget -qO- http://localhost:8090/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/mock-provider.jar"]
