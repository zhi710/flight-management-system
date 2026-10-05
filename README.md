# 航班管理系统（Flight Management System）

基于 **SpringBoot3 + Vue3** 的前后端分离航班票务综合管理平台，包含**旅客购票端**、**后台管理端**、**后端服务**三个独立模块，覆盖航班查询、在线购票、支付、值机选座、会员里程、航班动态等完整业务链路。

---

## 项目架构

```
flight-management-system/
├── Flight_management_system_us/     # 旅客购票端（Vue3 + TS + Element Plus）
├── Flight_management_system_ad/     # 后台管理端（Vue3 + TS + Element Plus + ECharts）
└── Flight_management_system_sp/     # 后端服务（SpringBoot3 + MyBatis-Plus）
```

**三端协作模式**：旅客端与管理端通过 RESTful API 与后端交互，航班状态变更通过 WebSocket 实时推送。

---

## 技术栈

### 后端服务（sp）
| 技术 | 说明 |
|---|---|
| SpringBoot 3 | 核心框架 |
| MyBatis-Plus | ORM 框架，简化 CRUD |
| MySQL | 主数据库 |
| Redis | 缓存、分布式锁、Token 存储 |
| WebSocket | 航班状态 / 通知实时推送 |
| JWT | 无状态鉴权（配合 Redis 实现主动失效） |
| 支付宝 SDK | 当面付（扫码支付）接入 |
| 阿里云短信 | 验证码登录 / 注册 |
| SpringDoc OpenAPI | 接口文档 |

### 前端（us / ad）
| 技术 | 说明 |
|---|---|
| Vue 3 + TypeScript | 核心框架 |
| Vite | 构建工具 |
| Element Plus | UI 组件库 |
| Pinia | 状态管理 |
| Vue Router | 路由与权限守卫 |
| ECharts | 运营数据可视化（管理端） |
| vue-i18n | 中英文国际化 |
| Axios | HTTP 请求（含拦截器统一处理） |

---

## 核心功能模块

### 旅客购票端
- **航班服务**：航班搜索、航班详情、航班动态查询、机场大屏
- **购票流程**：订单填写 → 在线支付（支付宝扫码）→ 出票 → 订单管理
- **值机服务**：在线值机、可视化选座、电子登机牌（二维码）
- **会员体系**：会员注册登录、里程管理、常旅客管理、个人中心
- **其他**：航班订阅、退改签、帮助中心、意见反馈、多语言切换

### 后台管理端
- **航班管理**：航班计划、航班调度、航班状态管理、航班日志
- **票务管理**：订单管理、票价管理、退票管理、舱位管理
- **旅客服务**：旅客信息、值机管理、登机口管理、座位管理、特殊服务
- **机组管理**：机组人员、排班调度、资质合规、飞行时长监控
- **运营监控**：航班地图、告警中心、异常航班处理、地面保障
- **数据报表**：收益分析、运营统计、自定义报表（ECharts 可视化）
- **系统管理**：用户权限、角色管理、基础数据、操作日志、系统配置

---

## 技术亮点

### 1. 航班状态机（核心业务建模）
以**枚举集中定义状态与转换表**作为"唯一事实来源"，统一处理状态流转的合法性校验、持久化与审计日志。

```
正常主流程：SCHEDULED → BOARDING → DEPARTED → (FLYING) → ARRIVED → COMPLETED
异常分支：  任意进行中状态 → DELAYED / CANCELLED
            DEPARTED / FLYING → DIVERTED / RETURNED
```

- **转换规则集中声明**（`Map` 定义每个状态的合法出边），避免规则散落导致状态机不完整、航班卡死
- **终止态防护**：`COMPLETED / CANCELLED / DIVERTED / RETURNED` 为终态，不可再流转
- **幂等处理**：同一状态重复设置视为 no-op；批量推进采用条件更新（`WHERE status = from`）天然幂等
- **审计日志**：每次状态变更记录 `FlightStatusLog`，可追溯
- **维度拆分**：值机状态独立为 `flight.checkin_status`，不混入运行状态机

### 2. 高并发防超卖（三层防护）
机票超卖是业务上不可接受的错误，采用三层递进式防护：

| 层级 | 方案 | 作用 |
|---|---|---|
| 第一层 | **Redis 分布式锁** | 锁粒度 `flight:{flightId}:{cabinClass}`，将同航班同舱位请求串行化 |
| 第二层 | **数据库乐观锁** | `flight_cabin.version` 字段 CAS 更新 |
| 第三层 | **SQL 条件扣减** | `UPDATE ... WHERE available_seats >= N`，数据库层保证不扣成负数 |

### 3. 支付网关抽象设计
采用**接口 + 多实现 + 路由器**模式，便于扩展新支付渠道：

```
PaymentGateway (接口)
├── AlipayGateway    # 支付宝当面付
└── MockPayGateway   # 模拟支付（本地开发 / 测试）

PaymentGatewayRouter  # 按渠道路由分发
```

**支付宝回调安全校验（三步）**：
1. **验签优先** —— 使用 `AlipaySignature.rsaCheckV1` 验签，**必须先于任何字段判断**（未验签就采信 `trade_status`，等于开放"任何人 POST 即可刷单"漏洞）
2. **校验 appId** —— 验签只证明报文来自支付宝，需进一步确认交易归属本应用
3. **处理业务** —— 判断 `TRADE_SUCCESS` / `TRADE_FINISHED` 后更新订单状态

### 4. 订单超时双保险
- **懒过期**：用户查询订单时实时判断并取消超时订单，保证看到的状态实时
- **定时兜底**：`OrderExpireScheduler` 每 60 秒扫描，清理"下单后不再访问"的漏网订单，释放座位库存

### 5. 实时推送
基于 **WebSocket** 实现服务端主动推送，替代轮询方案。Token 通过 URL query 参数传递，由 Handler 内部自校验。按业务场景拆分多个 Handler（旅客端航班状态 / 通知推送，管理端监控 / 异常航班推送）。

### 6. 工程规范实践
- **自建设计系统**：Design Token 统一全站色彩与样式规范，含多语言审计脚本
- **统一响应与异常**：统一返回体 + `@RestControllerAdvice` 全局异常拦截 + `ErrorCode` 枚举
- **AOP 操作日志**：自定义注解 + `@Around` 环绕通知，统一记录操作人、入参出参与耗时
- **接口压测**：编写压测脚本验证系统性能

---

## 代码规模

| 模块 | 规模 |
|---|---|
| 后端（sp） | 250+ Java 类，含 **32** 个 Controller、**35** 个 Service、**46** 个 Mapper、**45** 个实体类 |
| 管理端（ad） | 35+ 个页面 |
| 购票端（us） | 25+ 个页面 |

---

## 本地运行

### 后端
```bash
cd Flight_management_system_sp
# 配置 application.yml 中的 MySQL、Redis 连接信息
mvn clean package
java -jar target/*.jar
```
接口文档：`http://localhost:8080/swagger-ui.html`

### 前端
```bash
cd Flight_management_system_us   # 或 Flight_management_system_ad
npm install
npm run dev
```

---

## 说明

本项目为个人独立完成的毕业设计项目。开发过程中主导需求定义、技术方案设计与关键业务建模；编码环节采用 AI 编程工具辅助实现，并就方案合理性与技术选型（与主流方案对比）进行评审讨论后决策落地，独立完成代码集成、联调与调试。