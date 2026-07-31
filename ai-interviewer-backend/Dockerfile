# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先复制 pom.xml，利用 Docker 缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 从构建阶段复制 jar
COPY --from=build /app/target/*.jar app.jar

# 健康检查（使用 PORT 环境变量，Railway 动态分配端口）
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8999}/actuator/health || exit 1

EXPOSE 8999

ENTRYPOINT ["java", "-jar", "app.jar"]
