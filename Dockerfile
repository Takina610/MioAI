# MioAI 后端镜像（多阶段构建）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# 先独立下载依赖，利用 Docker 层缓存加速后续构建
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:21-jre
WORKDIR /app
# 非 root 运行
RUN useradd --system --uid 1001 mioai
USER mioai
COPY --from=build /app/target/mio-ai-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
