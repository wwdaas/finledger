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

MySQL V1、V2、V3、V4 脚本挂载到 `/docker-entrypoint-initdb.d`，只在全新数据卷初始化时按文件名
顺序执行；已有数据卷不会自动重复执行。旧环境升级时应先备份并按
[database-design.md](database-design.md) 依次应用尚未执行的迁移，不能通过删除数据卷代替迁移。

应用等待 MySQL 和 Redis 健康后启动。CI 使用 Java 17 执行全部 JUnit/Testcontainers 测试，
再验证 Docker 镜像可以构建。
