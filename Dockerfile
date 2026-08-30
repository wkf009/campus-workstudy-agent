FROM openjdk:21-jdk-slim AS backend-build
WORKDIR /app
COPY backend/pom.xml backend/
COPY backend/src backend/src/
RUN apt-get update && apt-get install -y maven && cd backend && mvn clean package -DskipTests

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=backend-build /app/backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
