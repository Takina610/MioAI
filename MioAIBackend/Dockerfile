# MioAI 后端镜像（多模块构建）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 国内构建加速：Central 走阿里云镜像（settings.xml 仅进构建阶段，不进运行镜像）
COPY settings.xml /root/.m2/settings.xml
# 多模块：先拷全部 pom 再拷源码，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
COPY mio-common/pom.xml mio-common/
COPY mio-system/pom.xml mio-system/
COPY mio-framework/pom.xml mio-framework/
COPY mio-agent/pom.xml mio-agent/
COPY mio-admin/pom.xml mio-admin/
RUN mvn dependency:go-offline -B -q
COPY mio-common/src mio-common/src
COPY mio-system/src mio-system/src
COPY mio-framework/src mio-framework/src
COPY mio-agent/src mio-agent/src
COPY mio-admin/src mio-admin/src
# application.yml 被 gitignore 排除，构建时从模板生成（所有敏感项由环境变量注入）
RUN test -f mio-admin/src/main/resources/application.yml || cp mio-admin/src/main/resources/application.yml.example mio-admin/src/main/resources/application.yml
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:21-jre
WORKDIR /app
# node/npx：STDIO 型 MCP 服务器（npx -y xxx）以子进程方式在容器内运行，运行镜像必须带 Node 运行时
RUN apt-get update \
    && apt-get install -y --no-install-recommends nodejs npm ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# 非 root 运行
RUN useradd --system --uid 1001 mioai
USER mioai
COPY --from=build /app/mio-admin/target/mio-admin-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
