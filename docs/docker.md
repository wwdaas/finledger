# Docker 与工程化

`Dockerfile` 使用 Java 17 多阶段构建：构建阶段由 Maven Wrapper 生成可执行 jar，运行
阶段只保留 JRE，并以非 root 的 `finledger` 用户启动。

开发基础设施：

```bash
docker compose up -d mysql redis
```

完整容器化应用：

```bash
docker compose --profile app up -d --build
```

MySQL 建表脚本挂载到 `/docker-entrypoint-initdb.d`，只在全新数据卷初始化时执行；已有
数据卷不会重复建表。应用等待 MySQL 和 Redis 健康后启动。CI 使用 Java 17 执行全部
JUnit/Testcontainers 测试，再验证 Docker 镜像可以构建。
