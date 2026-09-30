# ═══════════════════════════════════════════════════════
# Stage 1: Build
# ═══════════════════════════════════════════════════════
# Maven'ı içinde hazır getiren resmi imaj — sürüm, wrapper'ın kullandığıyla
# aynı (.mvn/wrapper/maven-wrapper.properties: 3.9.9).
#
# NEDEN mvnw DEĞİL? Depodaki mvnw elle yazılmış bir betik; Maven'ı indirip
# unzip ile açıyor. Alpine'deki unzip (busybox) dosya izinlerini korumadığı için
# bin/mvn çalıştırılabilir çıkmıyor ve derleme "Maven installation failed" ile
# duruyordu. Ayrıca Windows'ta CRLF ile checkout edilen mvnw burada "not found"
# veriyordu. Maven'ı konteynerde indirip kurmaya çalışmak yerine kurulu hâlini
# kullanmak iki sorunu da ortadan kaldırıyor.
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Önce sadece pom.xml — bağımlılıklar ayrı bir katmanda önbelleğe alınır;
# yalnızca kaynak kod değiştiğinde bu adım yeniden koşmaz.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Sonra kaynak kodu kopyala ve derle
COPY src/ src/
RUN mvn package -DskipTests -B

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

# Health check — uygulama PORT değişkenini dinliyor (bkz. application.yml);
# kontrol de aynı porta bakmalı, yoksa platform farklı bir port verdiğinde
# sağlıklı konteyner kendini sağlıksız sanar.
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health || exit 1

# JVM tuning — container-aware defaults
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
