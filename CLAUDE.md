# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供项目指导。

## 项目概述

这是一个**分布式唯一序列生成系统** - 基于 Java 的微服务，利用多个数据库实例的自增机制生成趋势递增的唯一 ID。

## 架构

项目采用多模块 Maven 架构，包含三个主要模块：

- **backend**: 核心序列生成服务（主服务）
- **interface**: 服务接口定义（用于 RPC 和 HTTP）
- **consumer**: 演示消费者应用

### 技术栈

| 技术 | 用途 |
|------|------|
| Java 17 | 开发语言 |
| Spring Boot 3.3.0 | 应用框架 |
| Apache Dubbo 3.3.0 | RPC 框架（tri 协议） |
| ZooKeeper 3.9.2 | 服务注册与配置中心 |
| MySQL 8.0 | 数据存储（2 个实例） |
| Maven | 构建工具 |

### 工作原理

系统使用 MySQL 的 `REPLACE INTO` 语句配合 MyISAM 引擎表：
- `REPLACE INTO` 删除已存在的行（如果存在）并插入新行
- 新的自增 ID 成为序列值
- **双 MySQL 实例**配置：
  - mysql-a: `auto_increment_offset=1`，`auto_increment_increment=2`（生成奇数 ID）
  - mysql-b: `auto_increment_offset=2`，`auto_increment_increment=2`（生成偶数 ID）

### 核心特性

- 业务 ID 隔离：不同 token 映射到不同表
- 双协议支持：HTTP 和 Dubbo（tri 协议，端口 11111）
- 多数据源适配：支持 HikariCP（默认）和 Druid
- 随机负载均衡：在两个数据源间随机路由
- AOP 故障转移：一个数据库失败时自动切换到另一个
- 基于 ZooKeeper 的动态配置

## 开发命令

### Docker 快速启动

```bash
docker compose up
```

### 构建

```bash
mvn clean package -DskipTests
```

## 核心文件

| 文件 | 用途 |
|------|------|
| `backend/src/main/java/cn/lz/seq/service/SeqServiceImpl.java` | 主序列生成入口 |
| `backend/src/main/java/cn/lz/seq/dao/SeqDao.java` | 使用 REPLACE INTO 的数据库访问 |
| `backend/src/main/java/cn/lz/seq/conf/DataSourceFailoverAspect.java` | 故障转移处理的 AOP 切面 |
| `backend/src/main/resources/application.yml` | 后端配置 |
| `docker-compose.yml` | Docker 编排（mysql-a、mysql-b、zookeeper、seq-generator） |

## API 使用

**HTTP 端点：**
```
GET http://localhost:11111/seq?token=video
GET http://localhost:11111/seq?token=myshop
```

**Dubbo RPC：**
```java
@DubboReference
private SeqService seqService;
long seq = seqService.getSeq("video");
```
