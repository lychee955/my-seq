# 分布式唯一序列系统

基于数据库主键自增生成趋势递增序列号

## 生成原理

使用 `REPLACE INTO` 语句插入数据时，如果唯一索引列有重复的话会删除重复的数据然后插入新的数据，新的主键 id 就可以作为生成的序列值。

不同数据库配置不同的起始值，多个数据库生成的 id 就不会重复。

本项目使用两个 MySQL 实例：

| 实例 | 起始值 | 步长 | 生成的序列 |
|-----|-------|-----|----------|
| mysql-a | 1 | 2 | 1, 3, 5, 7, 9... |
| mysql-b | 2 | 2 | 2, 4, 6, 8, 10... |

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.3.0 | 应用框架 |
| Apache Dubbo | 3.3.0 | RPC 框架（tri 协议） |
| ZooKeeper | 3.9.2 | 服务注册与配置中心 |
| MySQL | 8.0 | 数据存储（2 个实例） |
| Maven | - | 构建工具 |

## 项目功能

**已完成的**

- 实现了不同业务之间 id 隔离（不同 token 映射到不同表）
- 实现了从 ZooKeeper 读取 token 与表名映射关系
- ZooKeeper 初始化信息

| 路径 | 值 | 解释 |
|-----|----|-----|
| /seq/token | video=video_seq <br/>myshop=shop_seq | key 为 token，用于鉴权。value 为表名，用于取值 |
| /seq/strategy | random | 用于实时调整负载均衡策略，目前仅支持 random，即随机路由 |

- 提供简单的随机路由均衡策略
- 适配多数据源（HikariCP / Druid）
- 基于 AOP 实现故障转移（一个数据库失败时自动切换到另一个）
- 通过 ZooKeeper 配置负载均衡策略
- 支持 HTTP 或 Dubbo 调用（tri 协议，端口 11111）

**准备做的**

- 预取功能
- 批量 id 功能
- 提供其他负载均衡策略

## 各模块介绍

- **backend**
  序列生成服务，该服务暴露了 Dubbo/HTTP 接口。
- **consumer**
  演示服务，定时去请求 backend 暴露的服务接口，获取序列号。
- **interface**
  定义了服务接口，用于 RPC 发布与调用，同时该服务接口可通过 HTTP 调用。

## 一、Docker 快速启动

```bash
docker compose up
```

### 获取序列号方式

#### 1. HTTP

```
GET http://localhost:11111/seq?token=${token}
```

> 预置了两个表，token 可填 `video` 或 `myshop`，即：
> - http://localhost:11111/seq?token=video
> - http://localhost:11111/seq?token=myshop

#### 2. Dubbo

1. 引入 interface 模块
```xml
<dependency>
    <groupId>cn.lz.seq</groupId>
    <artifactId>interface</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

2. 基于 Dubbo 的 RPC 调用
```java
import cn.lz.seq.api.SeqService;
import org.apache.dubbo.config.annotation.DubboReference;

@DubboReference
private SeqService seqService;

void call(){
    String token = "video";
    long seq = seqService.getSeq(token);
    log.info("seq no : {}", seq);
}
```

---

> **注：**
>
> 获取序列请求入口：
> [SeqServiceImpl.java](backend/src/main/java/cn/lz/seq/service/SeqServiceImpl.java)

##### 以上两种调用方式详细示例请见 [consumer.Task.java](consumer/src/main/java/cn/lz/seq/demo/consumer/Task.java)

## 二、手动运行（不基于 Docker）

### 环境依赖

JDK 17，MySQL 8，ZooKeeper 3.9.2

### 程序配置

#### MySQL 配置

**mysql-a 实例**
```text
auto_increment_offset=1
auto_increment_increment=2
```

**mysql-b 实例**
```text
auto_increment_offset=2
auto_increment_increment=2
```

#### YAML 配置文件

##### 数据库连接

`backend/src/main/resources/application.yml`

```yaml
datasource:
  # 控制使用哪种数据库连接池
  useHikari: true
  hikari:
    one:
      jdbcUrl: jdbc:mysql://localhost:3306/seq?verifyServerCertificate=false&useSSL=true
      username: root
      password: 123456
      connectionTimeout: 1000
      minIdle: 20
      maxPoolSize: 20

    two:
      jdbcUrl: jdbc:mysql://localhost:3307/seq?verifyServerCertificate=false&useSSL=true
      username: root
      password: 123456
      connectionTimeout: 1000
      minIdle: 20
      maxPoolSize: 20
```

##### Dubbo 配置

**消费端** - `consumer/src/main/resources/application.yml`

```yaml
dubbo:
  application:
    name: seq-consumer
    logger: slf4j

  registry:
    address: zookeeper://localhost:2181
    register-mode: instance
```

**生产端** - `backend/src/main/resources/application.yml`

```yaml
dubbo:
  application:
    name: seq-provider
    logger: slf4j
  protocol:
    name: tri
    # HTTP 和 Dubbo 共用端口
    port: 11111
  registry:
    address: zookeeper://localhost:2181
    register-mode: instance
```

#### Curator 配置

用于从 ZooKeeper 中读取配置信息，例如负载均衡策略、token 值。

```yaml
curator:
  connectString: localhost:2181 # ZooKeeper 地址
  path: seq
  retryCount: 3 # 重试次数
  elapsedTimeMs: 2000 # 重试间隔时间
  sessionTimeoutMs: 120000 # Session 超时时间
  connectionTimeoutMs: 15000 # 连接超时时间
```

## 核心文件

| 文件 | 用途 |
|------|------|
| `backend/src/main/java/cn/lz/seq/service/SeqServiceImpl.java` | 主序列生成入口 |
| `backend/src/main/java/cn/lz/seq/dao/SeqDao.java` | 使用 REPLACE INTO 的数据库访问 |
| `backend/src/main/java/cn/lz/seq/conf/DataSourceFailoverAspect.java` | 故障转移处理的 AOP 切面 |
| `backend/src/main/resources/application.yml` | 后端配置 |
| `docker-compose.yml` | Docker 编排（mysql-a、mysql-b、zookeeper、seq-generator） |
