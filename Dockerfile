FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java","-Xmx512m","-jar","app.jar"]