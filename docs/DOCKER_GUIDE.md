# Docker 部署指南

## 服务架构

本项目使用 Docker Compose 编排以下服务：

| 服务 | 镜像 | 端口 | 说明 |
|-----|------|------|------|
| **Milvus** | milvusdb/milvus:v2.5.3 | 19530, 9091 | 向量数据库（原生支持混合检索 + 内置 BM25） |
| **Etcd** | quay.io/coreos/etcd:v3.5.12 | 2379 | Milvus 元数据存储 |
| **MinIO** | minio/minio:latest | 9000, 9001 | Milvus 对象存储 |
| **MySQL** | mysql:8.0 | 3306 | 关系型数据库 |
| **Redis** | redis:7.2-alpine | 6379 | 缓存 |
| **Attu** | zilliz/attu:v2.4.8 | 8000 | Milvus 可视化管理 |

**注意**：
- 文件存储使用本地文件系统（`./data/documents/` 和 `./data/images/`）
- MySQL 和 Redis 可使用本地客户端管理（Navicat、Another Redis Desktop Manager）

## 快速开始

### 启动前准备

1. **确认 Docker 已安装并运行**
   ```bash
   docker --version
   docker-compose --version
   docker ps
   ```

2. **检查端口是否被占用**（Windows）
   ```bash
   netstat -ano | findstr :3306
   netstat -ano | findstr :6379
   netstat -ano | findstr :19530
   netstat -ano | findstr :8000
   ```
   如果端口被占用，修改 `docker-compose.yml` 中的端口映射或停止占用程序。

3. **确认配置文件存在**
   ```bash
   ls config/milvus.yaml
   ls config/mysql/init.sql
   ls config/mysql/my.cnf
   ls config/redis/redis.conf
   ```

4. **确认数据目录已创建**
   ```bash
   ls -la data/
   ```
   应该看到：`data/milvus/`, `data/documents/`, `data/images/`

### 1. 启动所有服务

在项目根目录（`RAG-SERVER/`）打开终端：

```bash
# 启动所有服务（后台运行）
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志（可选）
docker-compose logs -f
```

**预计启动时间**：首次启动约 2-3 分钟（需要拉取镜像），Milvus 需要 60-90 秒初始化。

### 2. 验证服务

```bash
# 检查 Milvus
curl http://localhost:9091/healthz

# 检查 MySQL
docker-compose exec mysql mysql -uroot -prootpassword -e "SHOW DATABASES;"

# 检查 Redis
docker-compose exec redis redis-cli ping

# 检查 MinIO
curl http://localhost:9000/minio/health/live
```

## 服务访问

### 管理界面

- **Milvus Attu**: http://localhost:8000
  - 可视化管理 Milvus Collection、索引、数据
  
- **MinIO Console**: http://localhost:9001
  - 用户名: `minioadmin`
  - 密码: `minioadmin`
  - 用于查看 Milvus 存储的向量数据

### 本地客户端管理

建议使用以下工具：
- **MySQL**: 使用 Navicat 连接 `localhost:3306`
  - 连接后检查是否有 4 个表：`ingestion_history`, `image_index`, `query_history`, `collection_stats`
- **Redis**: 使用 Another Redis Desktop Manager 连接 `localhost:6379`

### Attu 管理界面使用

访问 http://localhost:8000，首次连接需要配置：
- **Milvus Address**: `milvus:19530`（注意：不是 localhost）
- 连接后可以查看 Collection、索引、数据统计

## 数据存储位置

### 可见数据（项目目录）
```
RAG-SERVER/data/
├── milvus/          # Milvus 日志和临时文件
├── documents/       # PDF 文档存储（应用写入）
└── images/          # 图片文件存储（应用写入）
```

### Docker 数据卷（Docker 管理）
- `rag-server_milvus-etcd-data` - Etcd 数据
- `rag-server_milvus-minio-data` - 向量数据（重要！）
- `rag-server_mysql-data` - MySQL 数据库
- `rag-server_redis-data` - Redis 缓存

**查看数据卷**：
```bash
docker volume ls
docker volume inspect rag-server_mysql-data
```

**预计总大小**：初始 ~500 MB，运行后可能增长到几 GB。

### 数据库连接信息

**MySQL**:
```
Host: localhost
Port: 3306
Database: rag_server
Username: raguser
Password: ragpassword
Root Password: rootpassword
```

**Redis**:
```
Host: localhost
Port: 6379
Password: (无，如需设置请修改 config/redis/redis.conf)
```

**Milvus**:
```
Host: localhost
Port: 19530
```

## 常用操作

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志（实时）
docker-compose logs -f milvus
docker-compose logs -f mysql
docker-compose logs -f redis

# 查看最近 100 行日志
docker-compose logs --tail=100 milvus
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart milvus
docker-compose restart mysql
```

### 停止服务

```bash
# 停止所有服务（保留数据）
docker-compose stop

# 停止特定服务
docker-compose stop attu redis-commander phpmyadmin

# 停止并删除容器（保留数据卷）
docker-compose down

# 停止并删除容器和数据卷（⚠️ 危险操作）
docker-compose down -v
```

### 数据备份

```bash
# 备份 MySQL 数据
docker-compose exec mysql mysqldump -uroot -prootpassword rag_server > backup_$(date +%Y%m%d).sql

# 恢复 MySQL 数据
docker-compose exec -T mysql mysql -uroot -prootpassword rag_server < backup_20250106.sql

# 备份 Redis 数据（RDB 快照）
docker-compose exec redis redis-cli BGSAVE

# 复制 Redis 备份文件
docker cp rag-redis:/data/dump.rdb ./backup/redis_dump_$(date +%Y%m%d).rdb
```

### 清理无用数据

```bash
# 清理未使用的 Docker 资源
docker system prune

# 清理所有未使用的镜像
docker image prune -a

# 清理所有未使用的数据卷
docker volume prune
```

## 性能调优

### Milvus 优化

编辑 `config/milvus.yaml`：

```yaml
# 增加缓存内存
queryNode:
  cache:
    memoryLimit: 4  # GB

# 调整 Segment 大小
dataCoord:
  segment:
    maxSize: 1024  # MB
```

重启 Milvus 生效：
```bash
docker-compose restart milvus
```

### MySQL 优化

编辑 `config/mysql/my.cnf`：

```ini
# 增加缓冲池大小（建议为物理内存的 50-70%）
innodb_buffer_pool_size = 2G

# 增加最大连接数
max_connections = 1000
```

重启 MySQL 生效：
```bash
docker-compose restart mysql
```

### Redis 优化

编辑 `config/redis/redis.conf`：

```conf
# 增加最大内存
maxmemory 1gb

# 调整淘汰策略
maxmemory-policy allkeys-lfu  # LFU（最不经常使用）
```

重启 Redis 生效：
```bash
docker-compose restart redis
```

## 监控

### 查看资源使用

```bash
# 查看所有容器资源使用情况
docker stats

# 查看特定容器
docker stats rag-milvus-standalone rag-mysql rag-redis
```

### Milvus 监控（Prometheus）

Milvus 暴露了 Prometheus 指标端点：

```bash
# 查看 Milvus 指标
curl http://localhost:9091/metrics
```

可以配合 Prometheus + Grafana 进行监控（需额外配置）。

## 故障排查

### Milvus 启动失败

**问题**: Milvus 容器反复重启

**解决方案**:
```bash
# 1. 查看日志
docker-compose logs milvus

# 2. 检查 Etcd 和 MinIO 是否正常
docker-compose ps etcd minio

# 3. 清理数据重新初始化
docker-compose down
sudo rm -rf ./data/milvus
docker-compose up -d
```

### MySQL 连接失败

**问题**: 应用无法连接 MySQL

**解决方案**:
```bash
# 1. 检查 MySQL 是否启动
docker-compose ps mysql

# 2. 测试连接
docker-compose exec mysql mysql -uraguser -pragpassword -e "SELECT 1;"

# 3. 检查数据库是否初始化
docker-compose exec mysql mysql -uroot -prootpassword -e "SHOW DATABASES;"

# 4. 如果没有初始化，手动执行
docker-compose exec mysql mysql -uroot -prootpassword < config/mysql/init.sql
```

### Redis 内存不足

**问题**: Redis 报错 OOM (Out of Memory)

**解决方案**:
```bash
# 1. 查看内存使用
docker-compose exec redis redis-cli INFO memory

# 2. 手动清理缓存
docker-compose exec redis redis-cli FLUSHDB

# 3. 增加 maxmemory 配置（编辑 config/redis/redis.conf）
maxmemory 2gb

# 4. 重启 Redis
docker-compose restart redis
```

### 端口冲突

**问题**: 端口被占用

**解决方案**:
```bash
# Windows 查看端口占用
netstat -ano | findstr :3306
netstat -ano | findstr :6379
netstat -ano | findstr :19530

# 修改 docker-compose.yml 中的端口映射
# 例如：将 3306:3306 改为 3307:3306
```

## 生产环境建议

### 安全配置

1. **设置 Redis 密码**：
   ```conf
   # config/redis/redis.conf
   requirepass your_secure_password
   ```

2. **修改 MySQL 默认密码**：
   ```yaml
   # docker-compose.yml
   MYSQL_ROOT_PASSWORD: your_secure_password
   MYSQL_PASSWORD: your_secure_password
   ```

3. **限制管理界面访问**：
   ```yaml
   # docker-compose.yml
   # 注释掉不需要的管理界面
   # - attu
   # - redis-commander
   # - phpmyadmin
   ```

### 数据持久化

确保以下目录定期备份：
- `./data/milvus` - Milvus 数据
- MySQL 数据卷（使用 mysqldump）
- Redis 数据卷（使用 RDB/AOF）

### 资源限制

在 `docker-compose.yml` 中添加资源限制：

```yaml
services:
  milvus:
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 8G
        reservations:
          cpus: '2'
          memory: 4G
```

## 升级服务

### 升级 Milvus

```bash
# 1. 备份数据
docker cp rag-milvus-standalone:/var/lib/milvus ./backup/milvus_backup

# 2. 停止服务
docker-compose stop milvus

# 3. 修改镜像版本
# 编辑 docker-compose.yml: image: milvusdb/milvus:v2.5.4

# 4. 拉取新镜像
docker-compose pull milvus

# 5. 启动服务
docker-compose up -d milvus

# 6. 验证
docker-compose logs milvus
```

### 升级 MySQL

```bash
# 1. 备份数据
docker-compose exec mysql mysqldump -uroot -prootpassword --all-databases > backup_all.sql

# 2. 停止并删除容器
docker-compose stop mysql
docker-compose rm mysql

# 3. 修改镜像版本并启动
docker-compose up -d mysql
```

## 开发环境 vs 生产环境

### 开发环境（当前配置）

- 使用 Docker Compose
- 包含管理界面（Attu、PHPMyAdmin 等）
- 数据存储在本地目录
- 使用默认密码

### 生产环境建议

- 使用 Kubernetes 或 Docker Swarm
- 移除管理界面或使用 VPN 限制访问
- 使用云存储（如 AWS S3、阿里云 OSS）
- 使用强密码和证书
- 配置监控和告警（Prometheus + Grafana）
- 配置日志收集（ELK Stack）
- 使用负载均衡（Nginx）

## 参考资源

- [Milvus 官方文档](https://milvus.io/docs)
- [MySQL 8.0 文档](https://dev.mysql.com/doc/refman/8.0/en/)
- [Redis 文档](https://redis.io/documentation)
- [Docker Compose 文档](https://docs.docker.com/compose/)
