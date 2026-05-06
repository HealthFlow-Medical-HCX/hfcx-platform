FROM maven:3.8.6-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /build/target/mock-payer.jar /app/mock-payer.jar
EXPOSE 8091
HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
    CMD wget -qO- http://localhost:8091/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/mock-payer.jar"]
