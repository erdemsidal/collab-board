# ═══════════════════════════════════════════════════════
# Stage 1: Build
# ═══════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Önce sadece pom.xml kopyala — dependency cache katmanı
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Sonra kaynak kodu kopyala ve build et
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ═══════════════════════════════════════════════════════
# Stage 2: Run
# ═══════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

# Güvenlik: root olmayan kullanıcı
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# Ownership değiştir
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM tuning — container-aware defaults
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
