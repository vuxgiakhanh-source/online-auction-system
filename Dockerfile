# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy POM files trước để cache dependency layer khi source chưa đổi
COPY pom.xml                    ./
COPY auction-common/pom.xml     auction-common/
COPY auction-server/pom.xml     auction-server/
COPY auction-client/pom.xml     auction-client/

# Download dependencies (layer được cache nếu pom không đổi)
RUN mvn -B dependency:go-offline --no-transfer-progress -q

# Copy source
COPY auction-common/src         auction-common/src
COPY auction-server/src         auction-server/src
COPY auction-client/src         auction-client/src

# Build, bỏ qua test (test chạy trong CI riêng)
RUN mvn -B package -DskipTests --no-transfer-progress \
    && echo "=== Built JARs ===" \
    && ls -lh auction-server/target/*.jar

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime (image nhỏ gọn, không chứa Maven/JDK)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

# Tạo user không root để chạy app
RUN groupadd --gid 1001 auction \
 && useradd  --uid 1001 --gid auction --no-create-home --shell /bin/false auction

WORKDIR /app

# Copy fat-JAR từ stage build
COPY --from=builder /build/auction-server/target/auction-server-*.jar app.jar

# Thư mục log và phân quyền
RUN mkdir -p /app/logs && chown -R auction:auction /app

USER auction

# Cổng WebSocket
EXPOSE 8080

# Cấu hình DB qua biến môi trường — không hardcode trong image
# Ví dụ: -e DB_URL=jdbc:mysql://db:3306/auction_db
ENV SERVER_PORT=8080 \
    DB_URL="" \
    DB_USERNAME="" \
    DB_PASSWORD=""

# JVM flags: container-aware memory, tắt DNS cache mặc định dài, graceful shutdown
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dnetworkaddress.cache.ttl=60", \
  "-jar", "app.jar"]