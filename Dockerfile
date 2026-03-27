# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app
COPY pom.xml .
# Dependency cache katmanı
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

LABEL maintainer="Radio Shell"
LABEL description="Terminal FM Radio Player - Türkiye & Dünya"

# ffmpeg (ffplay içerir) yükle
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/target/radio-shell-1.0.0.jar app.jar

# Favori ve özel istasyon verisi için volume
VOLUME ["/root/.radio-shell"]

# İnteraktif terminal gerekli
ENV TERM=xterm-256color

ENTRYPOINT ["java", "-jar", "app.jar"]
