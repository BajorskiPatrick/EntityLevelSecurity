# Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Copy internal library and locally install it
COPY els-library /app/els-library
WORKDIR /app/els-library
RUN mvn clean install -DskipTests

# Copy demo app and build it
COPY els-demo /app/els-demo
WORKDIR /app/els-demo
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/els-demo/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
