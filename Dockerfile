FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
RUN useradd --system --no-create-home app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]