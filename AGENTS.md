# AGENTS.md

## Project

Distributed unique sequence generator. Java 17, Spring Boot 3.3.0, Dubbo 3.3.0, dual MySQL 8.0, ZooKeeper 3.9.2, Maven multi-module.

## Build & Run

```bash
mvn clean package -DskipTests          # build all modules
docker compose up                       # full stack: mysql-a, mysql-b, zookeeper, app
```

No lint, typecheck, or CI workflows exist in this repo. `mvn clean package -DskipTests` is the only verification step.

## Architecture

Three Maven modules:

- **interface** — API definition only (`SeqService` interface). Both a `@RestController` and Dubbo service contract. `spring-web` is `provided` scope — do not add runtime deps here.
- **backend** — Main service. Entry point: `IDGeneratorApplication.java`. Excludes `DataSourceAutoConfiguration` and wires its own `DynamicDataSource`.
- **consumer** — Demo app, not part of the core. Shows both HTTP and Dubbo call patterns.

## Key mechanism

`REPLACE INTO <table> (stub) values ('0')` on a **MyISAM** table with a unique index on `stub`. The generated auto-increment ID becomes the sequence value. Tables **must** use MyISAM — InnoDB will not work.

Dual MySQL instances with offset/step (1,2 / 2,2) produce odd/even IDs. The `@DynamicSwitch` annotation + `DataSourceFailoverAspect` randomly routes between data sources and auto-fails-over on `CannotGetJdbcConnectionException`.

## Configuration

`backend/src/main/resources/application.yml` — all backend config. Env vars override defaults:

| Env var | Purpose | Default |
|---------|---------|---------|
| `SPRING_DATASOURCE_URL_1` | MySQL-a JDBC URL | `localhost:3306` |
| `SPRING_DATASOURCE_URL_2` | MySQL-b JDBC URL | `localhost:3307` |
| `SPRING_DATASOURCE_USERNAME` | DB user | `root` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `123456` |
| `CURATOR_CONNECTSTRING` | ZooKeeper address | `localhost:2181` |
| `REST_DUBBO_API_PORT` | HTTP + Dubbo tri port | `11111` |
| `SPRING_SERVER_PORT` | Spring Boot internal port | `9999` |

Connection pool switch: `datasource.useHikari` (true/false) selects HikariCP or Druid.

## ZooKeeper nodes

ZK holds runtime config under `/seq`:

| Path | Example value | Purpose |
|------|---------------|---------|
| `/seq/token` | `video=video_seq\nmyshop=shop_seq` | Token-to-table mapping (properties format, newline-separated) |
| `/seq/strategy` | `random` | Load balance strategy (currently only `random`) |

Init script: `docker/zk/init.sh`. Changes to `/seq/token` are watched live via `CuratorCache`.

## Files to know

| File | Role |
|------|------|
| `backend/.../IDGeneratorApplication.java` | Spring Boot main, `@EnableDubbo`, excludes `DataSourceAutoConfiguration` |
| `interface/.../SeqService.java` | API contract — `@RestController` + Dubbo interface |
| `backend/.../SeqServiceImpl.java` | Sequence generation entrypoint, resolves token→table via ZK |
| `backend/.../SeqDao.java` | `REPLACE INTO` execution, `@DynamicSwitch` triggers AOP routing |
| `backend/.../DataSourceFailoverAspect.java` | AOP aspect: random data source selection + failover |
| `backend/.../DynamicDataSource.java` | Extends `AbstractRoutingDataSource`, tracks fault/alive sources |
| `backend/.../DBSourceConfig.java` | Conditional bean config for HikariCP vs Druid |
| `backend/.../ZNodeWatcherService.java` | ZK watcher, loads token mapping and strategy |
| `docker/mysql/init.sql` | Creates `video_seq` and `shop_seq` tables (MyISAM) |

## Gotchas

- `interface` module: `spring-web` scope is `provided`. Do not make it `compile`.
- `SeqDao` validates table names against `^[a-zA-Z0-9_]+$` — new table names must match.
- The bean names `edenDataSource` and `oddDataSource` in `DBSourceConfig` are misleading — `oddDataSource` maps to `datasource.hikari.one` (the first DB), not the second.
- Failed data sources are blacklisted for 10 seconds before being re-added (`DynamicDataSource.handleFaultDataSource`).
- No tests exist. Running `mvn test` will succeed but tests nothing meaningful.
- To add a new sequence table: create the MyISAM table in both MySQL instances, add a token mapping entry in ZK `/seq/token`.
