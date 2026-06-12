# ── Build stage ──
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Runtime stage ──
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/logs
EXPOSE 8081
ENTRYPOINT ["java", \
    "-Djava.net.preferIPv4Stack=true", \
    "-Dnetworkaddress.cache.ttl=60", \
    "-Djdk.httpclient.keepalive.timeout=30", \
    "-Djdk.httpclient.connectionPoolSize=5", \
    "-jar", "app.jar", \
    "--spring.profiles.active=prod"]
