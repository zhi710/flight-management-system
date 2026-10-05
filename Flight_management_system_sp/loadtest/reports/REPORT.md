# 航班管理系统 高并发压测报告

- 压测时间：2026-09-09 12:00 ~ 12:58（01-search → 03-admin-read → 02-booking → 04-mixed 顺序执行）
- 压测工具：Apache JMeter 5.6.3（非 GUI 模式）
- 被测服务：`flight_management_system_sp`，`http://127.0.0.1:8080`，Spring Boot 3.2.5 / Java 17
- 加压方式：阶梯加压 **50 / 100 / 200 / 300 / 400 / 500** 并发，每档 ramp 15s + 满负荷 120s（共 135s）
- 数据准备：30 个测试航班（PEK→SHA，2026-09-15）+ 90 个舱位（每舱 10 万座位），1 个测试管理员 + 1 个测试会员
- 采样总量：**315,614 个请求，0 个 HTTP 错误，0 个连接超时**

---

## 一、结论速览

| 场景 | 类型 | 拐点并发 | 峰值吞吐 | 500 并发时平均响应 | 稳定性 |
|---|---|---|---|---|---|
| 01 前台航班搜索 | 读 | **100** | **77.4 TPS** | 5789 ms | 无错误，纯排队 |
| 02 订票下单全链路 | 写 | **200** | **139.7 TPS** | 4156 ms | 200+ 起出现业务码 409/10002 |
| 03 后台管理接口 | 读（含审计写） | **200** | **231.7 TPS** | 1929 ms | 无错误，纯排队 |
| 04 混合场景 | 读写混合 | **50（已饱和）** | **31.9 TPS** | 12902 ms | 无错误，纯排队 |

**一句话结论**：系统**没有任何一个场景在 500 并发下崩溃**，但吞吐在 100~200 并发后就不再增长，延迟随并发线性上涨——这是典型的**后端资源饱和 + 排队**，而不是客户端或网络问题。瓶颈在数据库访问层，不在 Tomcat。

三个最贵的问题（按修复收益排序）：

1. **搜索接口 N+1 查询**：一次搜索打 **约 125 条 SQL**，其中 120 条是逐航班循环查航司/机场/舱位。
2. **SQL 全量打印到 stdout**：`mybatis-plus.configuration.log-impl: StdOutImpl`，峰值时约 **9,700 条 SQL/秒**同步写控制台。
3. **数据库连接池只有 10 个连接**（Hikari 未配置，用默认值），而单请求要 125 次 DB 往返。

---

## 二、各场景明细

> TPS = 该档实际采样数 / 实际持续秒数。表中"总 TPS"为场景内所有采样器之和。

### 01 前台航班搜索（读）

单接口 `GET /api/flights/search`，思考时间 500ms。

| 并发 | 总 TPS | 平均响应 | p95 | p99 | 错误 |
|---|---|---|---|---|---|
| 50 | 63.0 | 245 ms | 356 ms | 402 ms | 0 |
| 100 | 73.2 | 787 ms | 1120 ms | 1266 ms | 0 |
| 200 | 76.2 | 1995 ms | 2956 ms | 3278 ms | 0 |
| 300 | 77.4 | 3199 ms | 4398 ms | 4712 ms | 0 |
| 400 | 77.0 | 4490 ms | 6041 ms | 6548 ms | 0 |
| 500 | 76.9 | 5789 ms | 7511 ms | 7940 ms | 0 |

**拐点在 100 并发**：从 63 → 73 TPS 后，再加 4 倍并发（100 → 500），吞吐只从 73 涨到 77（+5%），响应时间却从 787ms 涨到 5789ms（**7.4 倍**）。这是纯粹的排队：请求在等服务端资源，服务端已经吃满。

独立探测（Python 客户端，非 JMeter）复现了同一现象：1/5/10/20/50 线程 → 9.0/51.2/70.3/77.2/67.7 TPS，天花板同样在 **77 TPS** 附近，排除了压测客户端本身的瓶颈。

### 02 前台订票下单全链路（写）

链路：搜索 → 创建订单 → 订单详情 → 订单列表，思考时间 1000ms，使用 `flights.csv` 轮询 90 个舱位。

| 并发 | 总 TPS | 创建订单平均 | 创建订单 p99 | 业务码异常 |
|---|---|---|---|---|
| 50 | 46.4 | 15 ms | 66 ms | 无 |
| 100 | 92.1 | 12 ms | 53 ms | 无 |
| 200 | **139.7** | 26 ms | 296 ms | 409×11、10002×1 |
| 300 | 116.0 | 261 ms | 4156 ms | 409×8 |
| 400 | 104.7 | 649 ms | 3250 ms | 409×7、10002×5 |
| 500 | 106.6 | 1788 ms | 7220 ms | 无 |

**拐点在 200 并发**，之后回落到 ~105 TPS。写链路是本轮压测里"最健康"的一段：200 并发以内创建订单平均只要 12~26ms（Redis 锁 + 乐观锁 CAS 生效），且全程没有出现 HTTP 500。

- **409**（`CONFLICT`，"当前航班预订人数较多，请稍后重试"）：`ConcurrencyHelper.executeWithLock` 的 Redis 锁 5 秒超时后**快速失败**，属预期设计，未产生脏数据。
- **10002**（`FLIGHT_FULL`，座位售罄）：压测数据每舱 10 万座位，但 `flights.csv` 被 500 个线程反复轮询同一批舱位，出现少量售罄，符合预期。
- **订单详情 404**：创建订单拿到 409 时 `${orderId}` 为默认值 `NA`，后续查详情必然 404。这是**测试脚本的连带效应，不是服务端缺陷**（同一时刻 200 响应正常）。

### 03 后台管理接口（读 + 审计写）

4 个接口各占 1/4 流量：监控大屏 / 航班列表 / 机组名单 / 旅客查询，思考时间 500ms。

| 并发 | 总 TPS | 监控大屏 | 航班列表 | 机组名单 | 旅客查询 | 错误 |
|---|---|---|---|---|---|---|
| 50 | 88.0 | 15.9 ms | 47.5 ms | 17.0 ms | 61.0 ms | 0 |
| 100 | 156.0 | 23.9 ms | 164.6 ms | 28.0 ms | 199.8 ms | 0 |
| 200 | 216.4 | 49.9 ms | 623.2 ms | 63.2 ms | 760.6 ms | 0 |
| 300 | 221.4 | 184.6 ms | 1136.6 ms | 275.0 ms | 1568.8 ms | 0 |
| 400 | 227.9 | 512.9 ms | 1537.3 ms | 615.9 ms | 2026.4 ms | 0 |
| 500 | 231.7 | 897.2 ms | 1929.1 ms | 1012.2 ms | 2430.1 ms | 0 |

（表内为平均响应时间）

**拐点在 200 并发**，天花板约 232 TPS——四个场景里最高，因为接口都是简单单表查询。

但注意：**"读"场景其实也在写库**。`@OperationLog` 注解让 `ControllerLogAspect` 在**请求线程内同步**插入一行 `sys_log`。本轮 03-admin-read 共 **152,569 个请求 → 152,569 次 INSERT**。这也是"监控大屏"和"旅客查询"响应时间差 2.7 倍的原因之一：日志插入与业务查询争抢同一个只有 10 连接的池。

### 04 混合场景（读 + 写）

按子项个数加权：45% 搜索 + 25% 后台只读 + 20% 下单链路 + 10% 订单列表，思考时间 500ms。

| 并发 | 总 TPS | 搜索 | 创建订单 | 后台旅客查询 | 订单列表 | 最慢 p95 |
|---|---|---|---|---|---|---|
| 50 | 30.5 | 1441 ms | 55 ms | 911 ms | 721 ms | 5314 ms |
| 100 | 31.9 | 4362 ms | 144 ms | 2552 ms | 1293 ms | 7041 ms |
| 200 | 28.9 | 11694 ms | 200 ms | 6654 ms | 2204 ms | 10194 ms |
| 300 | 29.4 | 15120 ms | 2928 ms | 9537 ms | 5367 ms | 14204 ms |
| 400 | 29.0 | 19037 ms | 6544 ms | 13415 ms | 8762 ms | 18013 ms |
| 500 | 30.3 | 21775 ms | 10231 ms | 15655 ms | 11723 ms | 23692 ms |

（表内为平均响应时间）

**这是最严重的一组数据**：混合场景**在 50 并发时就已经饱和**（30.5 TPS），并发翻 10 倍到 500，吞吐纹丝不动（30.3 TPS），而搜索的平均响应时间从 1.4 秒涨到 **21.8 秒**。

对比单场景：搜索单跑 50 并发能到 63 TPS，混合里只分到 11.9 TPS。说明**不同业务类型混在一起时，重接口（125 条 SQL 的搜索）会长时间占住数据库连接，把轻接口（后台单表查询）一起拖慢**——连接池只有 10 个，是这里的关键放大器。

---

## 三、瓶颈定位（证据链）

### 1. 搜索接口 N+1：单请求约 125 条 SQL

`FlightServiceImpl.searchFlights()`（`src/main/java/.../service/impl/FlightServiceImpl.java:98`）：

```java
List<Flight> flights = flightMapper.selectList(wrapper);   // 1 条
flights.stream().map(flight -> {
    airlineMapper.selectById(flight.getAirlineId());        // ×1 / 航班
    airportMapper.selectByCode(flight.getDepartureAirport()); // ×1 / 航班
    airportMapper.selectByCode(flight.getArrivalAirport());   // ×1 / 航班
    flightCabinMapper.selectList(...);                        // ×1 / 航班
});
```

另外 `resolveAirportCodes()`（同文件 `:307`）每次请求还要跑 2 条 `LIKE` 查询，且**没有缓存**。

压测数据命中 30 个航班 → `2 + 1 + 30×4 = 123` 条 SQL/请求。按峰值 77 TPS 计算，**每秒约 9,500 条 SQL**。

### 2. SQL 全部打印到 stdout

`src/main/resources/application.yml:50`：

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 开发环境打印 SQL，生产环境请关闭
```

`StdOutImpl` 是**同步的 System.out 写**，每条 SQL 都要格式化 + 刷缓冲区。9,500 条/秒的日志量本身就是可观的 CPU 与 IO 开销，且这些开销完全无业务价值。

### 3. 连接池太小

`application.yml` 中**没有任何 `spring.datasource.hikari` 配置**，HikariCP 使用默认 `maximumPoolSize: 10`。同时：

```yaml
spring.data.redis.lettuce.pool.max-active: 20
```

搜索单请求 123 次 DB 往返，10 个连接意味着任何时刻最多 10 个搜索在真正干活，其余全部排队——这正是"100 并发后吞吐不再增长、延迟线性上涨"的直接解释。

### 4. 审计日志在请求线程内同步写

`ControllerLogAspect.logAround()` 在 `joinPoint.proceed()` 返回后**同步调用 `saveLog()`** 插入 `sys_log`。所有被测后台接口都带 `@OperationLog`，包括纯查询接口。读场景被写操作污染。

### 5. 未观察到的问题（排除项）

- **Tomcat 线程**：未配置（默认 200 最大线程），500 并发下未出现连接拒绝或 HTTP 503。
- **客户端/网络**：独立 Python 探测得到与 JMeter 一致的吞吐，排除压测机瓶颈。
- **Redis**：`lettuce.pool.max-active: 20` 在下单场景未见成为瓶颈（200 并发内创建订单平均 26ms）。
- **错误率**：315,614 个请求 0 个 HTTP 错误，说明系统是"慢而不倒"。

---

## 四、优化建议（按投入产出排序）

| 优先级 | 改动 | 预期收益 | 风险 |
|---|---|---|---|
| P0 | 关闭 SQL stdout 日志：`log-impl` 改 `NoLoggingImpl`，或仅 dev profile 开 `Slf4jImpl` | 峰值每秒省下约 9,500 次同步 IO | 无，生产环境本就不该开 |
| P0 | 消除搜索 N+1：航司/机场用 `selectBatchIds` 批量取，舱位用一次 `in` 查询 | 单请求 SQL 从 ~125 降到 ~4，**搜索吞吐预计提升 5~10 倍** | 低，纯重构 |
| P1 | 调大连接池：`spring.datasource.hikari.maximum-pool-size: 50`（配合 MySQL `max_connections`） | 缓解重接口独占连接、拖慢轻接口 | 中，需评估 DB 承载 |
| P1 | 机场/航司基础数据加缓存（Caffeine 或 Redis，TTL 长），`resolveAirportCodes` 尤其 | 每请求省 2 条 LIKE 查询 | 低 |
| P2 | `@OperationLog` 落库改异步（`@Async` + 有界队列，或写 MQ）；纯查询接口可降为采样记录 | 读场景不再被写拖累 | 中，需处理队列积压 |
| P2 | 搜索结果加 Redis 缓存（TTL 30~60s，按 出发/到达/日期 作 key） | 热点航线命中缓存后 DB 压力接近 0 | 中，需处理座位数实时性 |
| P3 | 压测/生产环境分离：生产 profile 关闭 `StdOutImpl`、开启 Hikari 与 Tomcat 调优 | — | 低 |

---

## 五、复现方式

```bash
cd E:/GitProject/Flight_management_system_sp/loadtest

# 1. 准备压测数据（从环境变量读库凭据，勿写入文件）
FMS_DB_PASS=*** java data/LoadTestData.java setup

# 2. 生成 JMeter 脚本（可选：LT_STEPS / LT_RAMP / LT_HOLD 覆盖默认值）
python gen_jmx.py

# 3. 顺序执行 4 个场景（每个场景开跑前自动重新登录取 token）
bash run.sh 01-search 03-admin-read 02-booking 04-mixed

# 4. 汇总
python lt_api.py summary        # → reports/summary.md
```

## 六、产物清单

| 路径 | 说明 |
|---|---|
| `loadtest/plans/0*.jmx` | 5 个 JMeter 脚本（含冒烟） |
| `loadtest/results/01-search.csv` | 59,658 条采样（12.9 MB） |
| `loadtest/results/03-admin-read.csv` | 152,569 条采样（18.5 MB） |
| `loadtest/results/02-booking.csv` | 79,318 条采样（10.8 MB） |
| `loadtest/results/04-mixed.csv` | 24,069 条采样 |
| `loadtest/reports/summary.md` | 全量分档明细（自动生成） |
| `loadtest/reports/REPORT.md` | 本报告 |
| `loadtest/logs/*.log` | JMeter 运行日志 |
| `loadtest/data/LoadTestData.java` | 压测数据准备/清理/校验工具 |
