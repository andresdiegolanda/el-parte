# Imagen de la aplicación. Compila con Java 25 dentro de Docker, así que en la
# máquina que la corre alcanza con Docker: no hace falta instalar Java ni Maven.

FROM maven:3.9.13-eclipse-temurin-25 AS compilacion
WORKDIR /fuente
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home elparte
WORKDIR /app
COPY --from=compilacion /fuente/target/el-parte.jar el-parte.jar
USER elparte
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/el-parte.jar"]
