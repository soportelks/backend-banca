
FROM maven:3.9.2-eclipse-temurin-17 AS build

WORKDIR /app

# Copiar pom.xml e instalar dependencias (cacheable)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y generar el JAR
COPY src ./src

# Construir el JAR ejecutable con Spring Boot (omitimos tests)
RUN mvn clean package -DskipTests


FROM gcr.io/distroless/java17-debian11:nonroot

# Seguridad: usar usuario no root
USER nonroot:nonroot

WORKDIR /app

# Zona horaria
COPY --from=build /usr/share/zoneinfo/America/Mexico_City /usr/share/zoneinfo/America/Mexico_City
ENV TZ=America/Mexico_City

# Fuente mínima (para PDFs o texto)
COPY --from=build /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf

# Usamos ARG para recibir el nombre del JAR desde el pipeline
ARG JAR_FILE=target/*.jar
COPY --from=build /app/${JAR_FILE} app.jar

# Exponer puerto
EXPOSE 9080

# Comando de inicio
ENTRYPOINT ["java", "-jar", "app.jar"]