FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -ntp package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 curricula
COPY --from=build /src/target/*.jar /app/app.jar
USER curricula
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "/app/app.jar"]
