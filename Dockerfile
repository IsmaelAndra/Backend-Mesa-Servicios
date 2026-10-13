# ==============================================================================
# Backend Mesa de Servicios COAC: imagen de producción
# ==============================================================================
# Dos etapas: en la primera se compila con el JDK y Gradle; en la segunda solo
# queda el JRE con el .jar, sin código fuente ni herramientas de compilación.
#
#   docker build -t ghcr.io/<usuario>/mesa-servicios-backend:1.0.0 .
#
# Las pruebas no se ejecutan aquí (las corre GitHub Actions en cada push).
# No lleva secretos: todo (base de datos, JWT, Graph) entra por variables de
# entorno al arrancar el contenedor.
# ==============================================================================

# ---------- Etapa 1: compilación ----------
FROM eclipse-temurin:17-jdk-jammy AS compilacion
WORKDIR /fuente

# Primero solo lo que define las dependencias: mientras build.gradle no cambie,
# Docker reutiliza esta capa y no vuelve a descargar todo en cada build.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Desde un clon de Windows, gradlew puede llegar con saltos de línea CRLF y
# Linux no lo ejecuta ("/bin/sh^M"). Se normaliza aquí.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /fuente/app.jar \;

# ---------- Etapa 2: ejecución ----------
FROM eclipse-temurin:17-jre-jammy

ARG VERSION=dev
LABEL org.opencontainers.image.title="mesa-servicios-backend" \
      org.opencontainers.image.description="Mesa de Servicios COAC - API Spring Boot" \
      org.opencontainers.image.source="https://github.com/IsmaelAndra/Backend-Mesa-Servicios" \
      org.opencontainers.image.version="${VERSION}"

# fontconfig + DejaVu: JasperReports dibuja los PDF con AWT y, sin fuentes del
# sistema, los informes fallan o salen sin texto. curl, para el healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl tzdata \
    && rm -rf /var/lib/apt/lists/*

# Usuario sin privilegios: si alguien llegara a ejecutar código en el proceso,
# no sería root dentro del contenedor.
RUN groupadd --system --gid 1001 mesa \
    && useradd --system --uid 1001 --gid mesa --home-dir /app --shell /usr/sbin/nologin mesa

WORKDIR /app
# uploads/ (adjuntos y branding) y logs/ son volúmenes: se crean aquí con el
# dueño correcto para que el volumen nuevo herede esos permisos.
RUN mkdir -p /app/uploads /app/logs && chown -R mesa:mesa /app

COPY --from=compilacion --chown=mesa:mesa /fuente/app.jar /app/app.jar

ENV TZ=America/Guayaquil \
    SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Duser.timezone=America/Guayaquil"

USER mesa
EXPOSE 8080
VOLUME ["/app/uploads", "/app/logs"]

# Arranque lento a propósito (start-period): la primera vez Flyway aplica todas
# las migraciones antes de abrir el puerto.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
