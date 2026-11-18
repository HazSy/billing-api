# Etapa 1: compilar con Maven y JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar primero el pom para aprovechar la caché de capas
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# Etapa 2: imagen final mínima
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
