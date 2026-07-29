# ============================================================
#  SIGAPP Backend - Conteo Fisico Movil
#  Build multi-stage: compila con Maven, corre solo con JRE.
# ============================================================

# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copiar primero el POM para cachear la descarga de dependencias.
# Mientras el pom.xml no cambie, Docker reutiliza esta capa.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:17-jre-jammy

# Zona horaria Colombia: las fechas de conteo (FI_COFIARAS) deben
# coincidir con la hora del ERP, no con UTC.
ENV TZ=America/Bogota \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Duser.timezone=America/Bogota -Dfile.encoding=UTF-8"

# tzdata: necesario para que America/Bogota exista dentro del contenedor.
# curl: lo usa el HEALTHCHECK de abajo.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata curl \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Usuario sin privilegios (requerido por la mayoria de PodSecurityPolicies)
RUN groupadd --system --gid 1001 sigapp \
    && useradd --system --uid 1001 --gid sigapp sigapp

COPY --from=build /build/target/*.jar app.jar
RUN chown sigapp:sigapp /app/app.jar

USER 1001

EXPOSE 8082

# Solo aplica a Docker/Compose. En Kubernetes se usan las probes del Deployment.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
