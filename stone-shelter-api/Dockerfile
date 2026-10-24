FROM eclipse-temurin:23.0.2_7-jdk-noble AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Database integration tests run separately with Testcontainers.
RUN sh ./mvnw -B -DskipTests package

FROM eclipse-temurin:23.0.2_7-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl=8.5.0-2ubuntu10.15 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/stone-shelter-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
