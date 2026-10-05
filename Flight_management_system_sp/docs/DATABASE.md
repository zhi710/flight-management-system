# 航班管理系统 - 数据库表结构文档

> 数据库：MySQL 8.0+  
> 字符集：utf8mb4  
> 表总数：36 张

---

## 目录

- [一、系统管理相关表（6张）](#一系统管理相关表6张)
- [二、基础数据表（5张）](#二基础数据表5张)
- [三、航班运营表（4张）](#三航班运营表4张)
- [四、订单与支付表（4张）](#四订单与支付表4张)
- [五、改签退票表（2张）](#五改签退票表2张)
- [六、值机与登机表（2张）](#六值机与登机表2张)
- [七、机组管理表（3张）](#七机组管理表3张)
- [八、会员相关表（3张）](#八会员相关表3张)
- [九、航班动态与订阅（1张）](#九航班动态与订阅1张)
- [十、系统配置与日志（4张）](#十系统配置与日志4张)
- [十一、新增表（Phase 2）（7张）](#十一新增表phase-27张)
- [表关系总览](#表关系总览)
- [状态枚举速查](#状态枚举速查)
- [测试账号](#测试账号)

---

## 一、系统管理相关表（6张）

### 1.1 sys_user — 旅客端用户表

存储旅客端注册用户的基本信息，支持手机号登录、会员等级管理、里程累计。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| user_id | VARCHAR(32) | 业务编号（如 U20260101001） |
| phone | VARCHAR(20) | 手机号（唯一，用于登录） |
| password | VARCHAR(128) | BCrypt 加密密码 |
| name | VARCHAR(64) | 姓名 |
| gender | VARCHAR(10) | 性别 MALE/FEMALE |
| birthday | DATE | 出生日期 |
| email | VARCHAR(128) | 邮箱 |
| avatar | VARCHAR(512) | 头像URL |
| member_level | VARCHAR(20) | 会员等级：NORMAL/SILVER/GOLD/PLATINUM |
| member_no | VARCHAR(32) | 常旅客号 |
| miles | INT | 累计里程 |
| status | TINYINT | 状态 1-正常 0-禁用 |
| deleted | TINYINT | 逻辑删除 |

---

### 1.2 sys_admin_user — 管理员用户表

存储管理端（后台）管理员账号，与旅客端用户完全隔离。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(64) | 登录用户名（唯一） |
| password | VARCHAR(128) | BCrypt 加密密码 |
| name | VARCHAR(64) | 姓名 |
| phone | VARCHAR(20) | 手机号 |
| email | VARCHAR(128) | 邮箱 |
| status | TINYINT | 状态 1-正常 0-禁用 |

---

### 1.3 sys_role — 角色表

定义系统中的角色类型，实现 RBAC 权限模型。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(64) | 角色名称 |
| code | VARCHAR(64) | 角色编码（唯一） |
| description | VARCHAR(256) | 角色描述 |

**预置角色：**

| 角色 | code | 说明 |
|------|------|------|
| 超级管理员 | SUPER_ADMIN | 拥有全部权限 |
| 航班调度员 | DISPATCHER | 航班调度相关权限 |
| 值机员 | CHECKIN_STAFF | 值机相关权限 |
| 客服 | CUSTOMER_SERVICE | 客服相关权限 |

---

### 1.4 sys_permission — 权限表

定义系统中的细粒度权限点，每个权限对应一个操作能力。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(64) | 权限名称 |
| code | VARCHAR(64) | 权限编码（唯一） |

**预置权限：**

| 权限 | code | 说明 |
|------|------|------|
| 航班读取 | flight:read | 查看航班信息 |
| 航班写入 | flight:write | 创建/编辑航班 |
| 航班取消 | flight:cancel | 取消航班 |
| 值机全部 | checkin:all | 值机操作权限 |
| 监控读取 | monitor:read | 查看监控数据 |
| 用户管理 | system:user | 管理用户 |
| 角色管理 | system:role | 管理角色 |
| 订单管理 | order:manage | 管理订单 |
| 退改审核 | ticket:approve | 审核退改签 |
| 报表查看 | report:read | 查看报表 |

---

### 1.5 sys_user_role — 用户-角色关联表

多对多关联表，将管理员用户与角色绑定。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 管理员ID |
| role_id | BIGINT | 角色ID |

---

### 1.6 sys_role_permission — 角色-权限关联表

多对多关联表，将角色与权限绑定。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| role_id | BIGINT | 角色ID |
| permission_id | BIGINT | 权限ID |

**权限模型关系链：**
```
管理员 → sys_user_role → 角色 → sys_role_permission → 权限
```

---

## 二、基础数据表（5张）

### 2.1 airport — 机场表

存储所有机场信息，是航班搜索、航线管理的基础数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| code | VARCHAR(10) | IATA 三字码（唯一，如 PEK、SHA） |
| name | VARCHAR(128) | 机场全名 |
| city | VARCHAR(64) | 所在城市 |
| province | VARCHAR(64) | 省份 |
| country | VARCHAR(64) | 国家（默认中国） |
| timezone | VARCHAR(32) | 时区（默认 Asia/Shanghai） |
| terminals | VARCHAR(128) | 航站楼（如 T1,T2,T3） |
| longitude | DECIMAL(10,6) | 经度 |
| latitude | DECIMAL(10,6) | 纬度 |

---

### 2.2 airline — 航空公司表

存储航空公司信息，航班关联航司，展示航司 Logo 和名称。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| code | VARCHAR(10) | 航司二字码（唯一，如 CA、MU、CZ） |
| name | VARCHAR(128) | 航司名称 |
| logo | VARCHAR(512) | Logo 图片地址 |
| country | VARCHAR(64) | 国家 |

---

### 2.3 aircraft_type — 机型表

存储飞机型号信息，用于座位图生成、运力规划。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| code | VARCHAR(20) | 机型代码（唯一，如 B738、A320） |
| name | VARCHAR(128) | 机型名称 |
| manufacturer | VARCHAR(64) | 制造商 |
| seat_layout | VARCHAR(20) | 座位布局（如 3-3） |
| total_seats | INT | 总座位数 |
| range_km | INT | 航程（公里） |
| cruise_speed | INT | 巡航速度（km/h） |

---

### 2.4 aircraft — 飞机表

存储每架具体飞机的信息，航班调度时指定具体执飞飞机。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| registration | VARCHAR(20) | 飞机注册号（唯一，如 B-1234） |
| aircraft_type_id | BIGINT | 关联机型ID |
| airline_id | BIGINT | 关联航司ID |
| manufacture_date | DATE | 出厂日期 |
| status | VARCHAR(20) | 状态：ACTIVE/MAINTENANCE/RETIRED |

---

### 2.5 route — 航线表

定义出发-到达机场对，是航班创建的基础数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| departure_airport | VARCHAR(10) | 出发机场代码 |
| arrival_airport | VARCHAR(10) | 到达机场代码 |
| distance_km | INT | 航线距离（公里） |
| flight_type | VARCHAR(20) | 航班类型：DOMESTIC/INTERNATIONAL |

> 同一对机场正反方向是两条航线（如 PEK→SHA 和 SHA→PEK）

---

## 三、航班运营表（4张）

### 3.1 flight — 航班表 ⭐ 核心表

存储每个航班的完整信息，是整个系统的核心业务表。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| flight_no | VARCHAR(20) | 航班号（如 CA1234） |
| flight_date | DATE | 航班日期 |
| departure_airport | VARCHAR(10) | 出发机场 |
| arrival_airport | VARCHAR(10) | 到达机场 |
| departure_terminal | VARCHAR(10) | 出发航站楼 |
| arrival_terminal | VARCHAR(10) | 到达航站楼 |
| departure_gate | VARCHAR(10) | 登机口 |
| departure_time | DATETIME | 出发时间 |
| arrival_time | DATETIME | 到达时间 |
| duration | INT | 飞行时长（分钟） |
| aircraft_id | BIGINT | 执飞飞机ID |
| airline_id | BIGINT | 所属航司ID |
| flight_type | VARCHAR(20) | 航班类型：DOMESTIC/INTERNATIONAL |
| status | VARCHAR(20) | 航班状态 |
| stops | INT | 经停次数（0=直飞） |
| punctuality | INT | 准点率百分比 |
| meal_provided | TINYINT | 是否提供餐食 |
| wifi | TINYINT | 是否有WiFi |
| power | TINYINT | 是否有充电 |
| remark | VARCHAR(512) | 备注 |
| version | INT | 乐观锁版本号 |
| iata_delay_code | VARCHAR(10) | IATA延误原因代码 |
| compensation_rule | VARCHAR(512) | 补偿规则 |
| actual_departure_time | DATETIME | 实际出发时间 |
| actual_arrival_time | DATETIME | 实际到达时间 |

---

### 3.2 flight_cabin — 航班舱位表 ⭐ 并发控制核心

存储每个航班各舱位的票价、库存、退改规则。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| flight_id | BIGINT | 关联航班ID |
| cabin_class | VARCHAR(20) | 舱位等级：ECONOMY/BUSINESS/FIRST |
| cabin_name | VARCHAR(32) | 舱位名称（经济舱/公务舱/头等舱） |
| fare | DECIMAL(10,2) | 票价 |
| tax | DECIMAL(10,2) | 税费 |
| total_price | DECIMAL(10,2) | 总价 |
| total_seats | INT | 该舱位总座位数 |
| available_seats | INT | 剩余可用座位数 |
| baggage | VARCHAR(64) | 免费行李额度 |
| refund_rule | VARCHAR(512) | 退票规则 |
| change_rule | VARCHAR(512) | 改签规则 |
| version | INT | 乐观锁版本号（防止超卖） |
| advance_purchase_days | INT | 提前购买天数限制 |
| blackout_start | DATE | 禁用起始日期 |
| blackout_end | DATE | 禁用结束日期 |
| corporate_fare | DECIMAL(10,2) | 企业协议价 |

> 下单时扣减 `available_seats`，使用乐观锁防止并发超卖

---

### 3.3 flight_status_log — 航班状态日志表

记录航班每次状态变更，用于审计追踪。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| flight_id | BIGINT | 关联航班ID |
| old_status | VARCHAR(20) | 原状态 |
| new_status | VARCHAR(20) | 新状态 |
| reason | VARCHAR(512) | 变更原因 |
| operator_id | BIGINT | 操作人ID |
| operator_name | VARCHAR(64) | 操作人姓名 |

---

### 3.4 seat — 座位表

存储每个航班的具体座位信息，支持值机选座。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| flight_id | BIGINT | 关联航班ID |
| row_num | INT | 排号（如 12） |
| col_code | VARCHAR(4) | 列号（如 A、B、C） |
| cabin_class | VARCHAR(20) | 所属舱位 |
| seat_type | VARCHAR(20) | 座位类型：WINDOW/AISLE/MIDDLE |
| extra_fee | DECIMAL(10,2) | 付费座位额外费用 |
| status | VARCHAR(20) | 状态：AVAILABLE/OCCUPIED |
| passenger_id | BIGINT | 占用旅客ID |

---

## 四、订单与支付表（4张）

### 4.1 t_order — 订单表 ⭐ 核心表

存储旅客的机票订单，贯穿预订→支付→出票→值机→完成的全生命周期。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_id | VARCHAR(32) | 业务订单号（如 ORD20260605001） |
| order_no | VARCHAR(32) | 订单编号 |
| pnr | VARCHAR(20) | 订座编号（如 ABC123） |
| user_id | BIGINT | 下单用户ID |
| flight_id | BIGINT | 关联航班ID |
| flight_no | VARCHAR(20) | 航班号 |
| cabin_class | VARCHAR(20) | 预订舱位 |
| search_id | VARCHAR(32) | 搜索ID |
| contact_name | VARCHAR(64) | 联系人姓名 |
| contact_phone | VARCHAR(20) | 联系电话 |
| contact_email | VARCHAR(128) | 联系邮箱 |
| status | VARCHAR(20) | 订单状态 |
| fare | DECIMAL(10,2) | 票价 |
| tax | DECIMAL(10,2) | 税费 |
| service_fee | DECIMAL(10,2) | 服务费 |
| total_amount | DECIMAL(10,2) | 总金额 |
| expire_time | DATETIME | 支付超时时间 |
| paid_time | DATETIME | 支付时间 |
| cancel_reason | VARCHAR(256) | 取消原因 |
| rebooked_from_id | BIGINT | 改签来源订单ID |
| ssr_flag | TINYINT | 是否有特殊服务申请 |

---

### 4.2 order_passenger — 订单旅客表

存储订单中每位旅客的信息。一个订单可以有多位旅客。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_id | BIGINT | 关联订单ID |
| passenger_name | VARCHAR(64) | 旅客姓名 |
| gender | VARCHAR(10) | 性别 |
| birthday | DATE | 出生日期 |
| id_type | VARCHAR(32) | 证件类型 |
| id_number | VARCHAR(64) | 证件号码 |
| phone | VARCHAR(20) | 手机号 |
| email | VARCHAR(128) | 邮箱 |
| passenger_type | VARCHAR(20) | 旅客类型：ADULT/CHILD/INFANT |
| frequent_flyer_no | VARCHAR(32) | 常旅客号 |
| ticket_no | VARCHAR(32) | 出票后生成的票号 |
| seat_row | INT | 值机后分配的座位排 |
| seat_column | VARCHAR(4) | 值机后分配的座位列 |
| checkin_status | VARCHAR(20) | 值机状态：NOT_CHECKED_IN/CHECKED_IN |

---

### 4.3 order_service_item — 订单附加服务表

存储订单中购买的附加服务（额外行李、保险、餐食等）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| order_id | BIGINT | 关联订单ID |
| service_type | VARCHAR(20) | 服务类型：BAGGAGE/INSURANCE/SEAT/MEAL/LOUNGE |
| service_code | VARCHAR(32) | 服务代码（如 BAG20 = 20kg 行李） |
| service_name | VARCHAR(64) | 服务名称 |
| quantity | INT | 数量 |
| unit_price | DECIMAL(10,2) | 单价 |
| total_price | DECIMAL(10,2) | 总价 |
| passenger_index | INT | 关联旅客索引 |

---

### 4.4 payment — 支付表

记录每笔支付的完整信息，支持多种支付方式。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| payment_id | VARCHAR(32) | 支付单号（如 PAY20260605001） |
| order_id | BIGINT | 关联订单ID |
| pay_method | VARCHAR(20) | 支付方式：WECHAT/ALIPAY/BANK_CARD/CREDIT |
| amount | DECIMAL(10,2) | 支付金额 |
| status | VARCHAR(20) | 支付状态：PENDING/SUCCESS/FAILED |
| transaction_id | VARCHAR(64) | 第三方支付交易号 |
| pay_url | VARCHAR(512) | 支付链接 |
| qr_code | VARCHAR(512) | 二维码地址 |
| expire_time | DATETIME | 支付超时时间 |
| paid_time | DATETIME | 支付成功时间 |
| callback_data | TEXT | 支付回调原始数据（用于对账） |

---

## 五、改签退票表（2张）

### 5.1 order_change — 改签记录表

记录旅客的改签申请，包括原航班→新航班、费用计算、审核状态。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| change_id | VARCHAR(32) | 改签单号（如 CHG20260607001） |
| order_id | BIGINT | 关联订单ID |
| original_flight_id | BIGINT | 原航班ID |
| new_flight_id | BIGINT | 新航班ID |
| original_cabin_class | VARCHAR(20) | 原舱位 |
| new_cabin_class | VARCHAR(20) | 新舱位 |
| segment_index | INT | 改签航段索引 |
| change_fee | DECIMAL(10,2) | 改签手续费 |
| fare_diff | DECIMAL(10,2) | 票价差额 |
| total_fee | DECIMAL(10,2) | 总费用 |
| status | VARCHAR(20) | 状态：PENDING/APPROVED/REJECTED |
| operator_id | BIGINT | 审核人ID |

---

### 5.2 order_refund — 退票记录表

记录旅客的退票申请，包括退款金额计算、审核状态。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| refund_id | VARCHAR(32) | 退票单号（如 REF20260607001） |
| order_id | BIGINT | 关联订单ID |
| reason | VARCHAR(256) | 退票原因 |
| refund_amount | DECIMAL(10,2) | 退款金额 |
| refund_fee | DECIMAL(10,2) | 退票手续费 |
| status | VARCHAR(20) | 状态：PENDING/APPROVED/REJECTED |
| operator_id | BIGINT | 审核人ID |

---

## 六、值机与登机表（2张）

### 6.1 check_in — 值机记录表

记录值机操作，关联订单和航班。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| checkin_id | VARCHAR(32) | 值机单号（如 CHK20260609001） |
| order_id | BIGINT | 关联订单ID |
| flight_id | BIGINT | 关联航班ID |
| user_id | BIGINT | 用户ID |
| status | VARCHAR(20) | 状态：CHECKED_IN |

---

### 6.2 boarding_pass — 登机牌表

存储电子登机牌信息，值机成功后生成。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| checkin_id | BIGINT | 关联值机记录ID |
| passenger_name | VARCHAR(64) | 旅客姓名 |
| flight_no | VARCHAR(20) | 航班号 |
| seat | VARCHAR(10) | 座位号（如 12A） |
| gate | VARCHAR(10) | 登机口 |
| boarding_time | DATETIME | 登机时间 |
| qr_code | VARCHAR(512) | 二维码地址 |
| barcode | VARCHAR(128) | 条形码内容 |

---

## 七、机组管理表（3张）

### 7.1 crew — 机组人员表

存储飞行员、副驾驶、乘务员等机组人员的基本信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| crew_id | VARCHAR(32) | 机组人员编号（如 C20260101001） |
| name | VARCHAR(64) | 姓名 |
| gender | VARCHAR(10) | 性别 |
| phone | VARCHAR(20) | 手机号 |
| email | VARCHAR(128) | 邮箱 |
| department | VARCHAR(64) | 所属部门（飞行部/客舱部） |
| status | VARCHAR(20) | 状态：FLYING/STANDBY/REST/TRAINING |

---

### 7.2 crew_qualification — 机组资质表

管理机组人员的飞行执照、机型资质、体检记录等。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| crew_id | BIGINT | 关联机组人员ID |
| type | VARCHAR(20) | 资质类型：LICENSE/AIRCRAFT/MEDICAL |
| name | VARCHAR(128) | 资质名称（如 B738机长资质） |
| number | VARCHAR(64) | 资质编号 |
| level | VARCHAR(20) | 等级：CAPTAIN/FO/SENIOR |
| issue_date | DATE | 颁发日期 |
| expire_date | DATE | 过期日期 |
| status | VARCHAR(20) | 状态：VALID/EXPIRING_SOON/EXPIRED |

---

### 7.3 crew_schedule — 机组排班表

记录机组人员与航班的排班关系。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| crew_id | BIGINT | 关联机组人员ID |
| flight_id | BIGINT | 关联航班ID |
| role | VARCHAR(20) | 角色：CAPTAIN（机长）/FO（副驾）/FA（乘务员） |
| schedule_date | DATE | 排班日期 |
| status | VARCHAR(20) | 状态：ASSIGNED |

---

## 八、会员相关表（3张）

### 8.1 passenger_document — 旅客证件表

存储用户绑定的证件信息，下单时可快速选择。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 关联用户ID |
| doc_type | VARCHAR(32) | 证件类型：ID_CARD/PASSPORT/HM_PASSPORT/TAIWAN_PASSPORT |
| doc_number | VARCHAR(64) | 证件号码 |
| name | VARCHAR(64) | 证件姓名 |
| nationality | VARCHAR(64) | 国籍 |
| expire_date | DATE | 证件有效期 |

---

### 8.2 frequent_traveler — 常用旅客表

存储用户的常用同行人信息，下单时可快速填入。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 关联用户ID |
| name | VARCHAR(64) | 姓名 |
| gender | VARCHAR(10) | 性别 |
| birthday | DATE | 出生日期 |
| id_type | VARCHAR(32) | 证件类型 |
| id_number | VARCHAR(64) | 证件号码 |
| phone | VARCHAR(20) | 手机号 |
| passenger_type | VARCHAR(20) | 旅客类型：ADULT/CHILD/INFANT |
| frequent_flyer_no | VARCHAR(32) | 常旅客号 |

---

### 8.3 member_miles_record — 里程变动记录表

记录用户里程的每次变动明细。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 关联用户ID |
| type | VARCHAR(20) | 类型：EARN（获取）/REDEEM（兑换） |
| miles | INT | 里程数（获取为正，兑换为负） |
| description | VARCHAR(256) | 描述（如 "CA1234 北京-上海 经济舱"） |
| order_id | BIGINT | 关联订单ID |

---

## 九、航班动态与订阅（1张）

### 9.1 flight_subscription — 航班动态订阅表

存储用户对航班动态的订阅关系。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 关联用户ID |
| flight_no | VARCHAR(20) | 订阅的航班号 |
| flight_date | DATE | 航班日期 |
| channels | VARCHAR(128) | 推送渠道：SMS/EMAIL/PUSH/WECHAT |
| types | VARCHAR(256) | 订阅类型：DELAY/GATE_CHANGE/BOARDING/CANCEL |
| status | TINYINT | 状态 1-启用 0-禁用 |

---

## 十、系统配置与日志（4张）

### 10.1 sys_config — 系统配置表

存储系统业务规则配置，支持动态调整。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| config_key | VARCHAR(128) | 配置键（唯一） |
| config_value | TEXT | 配置值（JSON 格式） |
| description | VARCHAR(256) | 配置说明 |

**预置配置：**

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| checkin_open_hours | 值机开放时间（起飞前小时数） | 24 |
| boarding_close_minutes | 登机口关闭时间（起飞前分钟数） | 15 |
| order_expire_minutes | 订单支付超时时间（分钟） | 15 |
| default_refund_rule | 默认退票手续费比例 | before2h: 10% |
| max_passengers_per_order | 每单最大旅客数 | 9 |
| sms_daily_limit | 每手机号每日短信上限 | 10 |

---

### 10.2 sys_log — 操作日志表

记录管理员的所有操作，用于审计追踪。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| operator_id | BIGINT | 操作人ID |
| operator_name | VARCHAR(64) | 操作人姓名 |
| module | VARCHAR(64) | 操作模块 |
| action | VARCHAR(64) | 操作类型：CREATE/UPDATE/DELETE/APPROVE 等 |
| target | VARCHAR(128) | 操作对象 |
| detail | TEXT | 操作详情 |
| ip | VARCHAR(64) | 操作IP |

---

### 10.3 alert — 告警表

存储系统产生的告警信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| level | VARCHAR(20) | 级别：URGENT/IMPORTANT/NORMAL/INFO |
| type | VARCHAR(64) | 类型：DELAY/WEATHER/MECHANICAL/SYSTEM |
| title | VARCHAR(256) | 告警标题 |
| content | TEXT | 告警内容 |
| status | VARCHAR(20) | 状态：PENDING/RESOLVED |
| resolve_time | DATETIME | 处理时间 |
| resolver_id | BIGINT | 处理人ID |
| ref_id | BIGINT | 关联业务ID |
| ref_type | VARCHAR(64) | 关联业务类型 |

---

### 10.4 feedback — 投诉建议表

存储旅客提交的投诉和建议。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 提交用户ID |
| type | VARCHAR(20) | 类型：COMPLAINT（投诉）/SUGGESTION（建议） |
| order_id | BIGINT | 关联订单ID |
| content | TEXT | 内容 |
| contact_phone | VARCHAR(20) | 联系电话 |
| status | VARCHAR(20) | 状态：PENDING/RESOLVED |
| reply | TEXT | 回复内容 |

---

### 10.5 gate — 登机口表

管理登机口的分配和使用情况。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| gate_code | VARCHAR(10) | 登机口编号（如 A12） |
| terminal | VARCHAR(10) | 所属航站楼（如 T3） |
| flight_id | BIGINT | 当前分配的航班ID |
| start_time | DATETIME | 使用开始时间 |
| end_time | DATETIME | 使用结束时间 |
| status | VARCHAR(20) | 状态：AVAILABLE/OCCUPIED |

---

## 十一、新增表（Phase 2）（7张）

### 11.1 irregular_operation — 不正常航班操作记录

记录航班不正常（延误/取消/备降/返航）的操作处理记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| flight_id | BIGINT | 航班ID |
| type | VARCHAR(20) | 类型：DELAY/CANCEL/DIVERT/RETURN |
| iata_delay_code | VARCHAR(10) | IATA延误原因代码 |
| reason | VARCHAR(512) | 具体原因 |
| delay_minutes | INT | 延误分钟数 |
| compensation_type | VARCHAR(32) | 补偿类型 |
| compensation_amount | DECIMAL(10,2) | 补偿金额 |
| auto_rebooked | TINYINT | 是否自动改签 |
| notify_passengers | TINYINT | 是否已通知旅客 |
| status | VARCHAR(20) | 状态 |
| operator_id | BIGINT | 操作人 |
| create_time | DATETIME | 创建时间 |

---

### 11.2 auto_rebooking — 自动改签记录

存储因航班不正常触发自动/手动改签的记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| original_order_id | BIGINT | 原订单ID |
| new_flight_id | BIGINT | 新航班ID |
| new_cabin_class | VARCHAR(20) | 新舱位 |
| passenger_name | VARCHAR(64) | 旅客姓名 |
| passenger_id_type | VARCHAR(32) | 旅客证件类型 |
| passenger_id_number | VARCHAR(64) | 旅客证件号码 |
| fare_diff | DECIMAL(10,2) | 票价差额 |
| auto_process | TINYINT | 是否自动处理 |
| status | VARCHAR(20) | 状态：PENDING/APPROVED/REJECTED/CONFIRMED |
| operator_id | BIGINT | 操作人 |
| execute_time | DATETIME | 执行时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

---

### 11.3 special_service_request — 特殊旅客服务(SSR)

记录旅客申请的特殊服务需求（如轮椅、无陪儿童等）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| order_id | BIGINT | 订单ID |
| order_passenger_id | BIGINT | 旅客ID |
| passenger_name | VARCHAR(64) | 旅客姓名 |
| ssr_code | VARCHAR(10) | SSR代码 |
| status | VARCHAR(20) | 状态：PENDING/APPROVED/REJECTED/COMPLETED |
| remark | VARCHAR(512) | 备注 |
| operator_id | BIGINT | 操作人 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

---

### 11.4 ssr_code — SSR代码字典

定义特殊服务请求的标准代码及其详细信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| code | VARCHAR(10) | SSR代码（唯一） |
| category | VARCHAR(32) | 分类 |
| name_cn | VARCHAR(128) | 中文名称 |
| name_en | VARCHAR(128) | 英文名称 |
| description | VARCHAR(512) | 说明 |
| extra_fee | DECIMAL(10,2) | 附加费用 |
| sort_order | INT | 排序 |
| status | TINYINT | 启用状态 |

---

### 11.5 ground_handling — 地面保障节点

记录航班地面保障各节点（上客、配餐、加油等）的完成情况。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| flight_id | BIGINT | 航班ID |
| node_code | VARCHAR(32) | 节点编码 |
| node_name | VARCHAR(64) | 节点名称 |
| plan_time | DATETIME | 计划时间 |
| actual_time | DATETIME | 实际时间 |
| delay_minutes | INT | 延误分钟 |
| status | VARCHAR(20) | 状态：PENDING/IN_PROGRESS/COMPLETED/FAILED |
| operator_id | BIGINT | 操作人 |
| remark | VARCHAR(512) | 备注 |

---

### 11.6 crew_flight_time — 机组飞行时间

记录机组人员的飞行时长和值勤时长，用于疲劳管理。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| crew_id | BIGINT | 机组ID |
| flight_date | DATE | 飞行日期 |
| flight_hours | INT | 飞行分钟数 |
| duty_hours | INT | 值勤分钟数 |
| rest_required | TINYINT | 是否需强制休息 |
| flight_id | BIGINT | 关联航班 |
| role | VARCHAR(20) | 角色 |

---

### 11.7 notification_log — 通知发送记录

记录系统向用户/管理员发送的所有通知。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（雪花ID） |
| target_user_id | BIGINT | 目标用户ID |
| target_admin_id | BIGINT | 目标管理员ID |
| target_phone | VARCHAR(20) | 目标手机号 |
| target_email | VARCHAR(128) | 目标邮箱 |
| channel | VARCHAR(20) | 渠道：SMS/EMAIL/APP_PUSH/WEBSOCKET |
| template_code | VARCHAR(64) | 模板代码 |
| title | VARCHAR(256) | 标题 |
| content | TEXT | 内容 |
| status | VARCHAR(20) | 状态：PENDING/SENT/FAILED/READ |
| send_time | DATETIME | 发送时间 |
| read_time | DATETIME | 读取时间 |
| error_msg | VARCHAR(512) | 错误信息 |
| ref_type | VARCHAR(64) | 关联业务类型 |
| ref_id | BIGINT | 关联业务ID |

---

## 表关系总览

```
┌─────────────────────────────────────────────────────────────────┐
│                        基础数据层                                │
│                                                                 │
│  airport ←──── route ────→ airport                              │
│  airline ←──── aircraft ────→ aircraft_type                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        航班运营层                                │
│                                                                 │
│  flight ────→ airline + aircraft + route                        │
│  flight_cabin ────→ flight（每个航班多个舱位）                   │
│  seat ────→ flight（每个航班多个座位）                           │
│  flight_status_log ────→ flight                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        订单业务层                                │
│                                                                 │
│  t_order ────→ flight + sys_user                                │
│  order_passenger ────→ t_order（一个订单多个旅客）               │
│  order_service_item ────→ t_order（附加服务）                    │
│  payment ────→ t_order                                          │
│  order_change ────→ t_order（改签）                             │
│  order_refund ────→ t_order（退票）                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        值机登机层                                │
│                                                                 │
│  check_in ────→ t_order + flight                                │
│  boarding_pass ────→ check_in                                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        机组管理层                                │
│                                                                 │
│  crew_schedule ────→ crew + flight                              │
│  crew_qualification ────→ crew                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                          会员层                                  │
│                                                                 │
│  passenger_document ────→ sys_user                              │
│  frequent_traveler ────→ sys_user                               │
│  member_miles_record ────→ sys_user                             │
│  flight_subscription ────→ sys_user                             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        系统管理层                                │
│                                                                 │
│  sys_user_role ────→ sys_admin_user + sys_role                  │
│  sys_role_permission ────→ sys_role + sys_permission            │
│  sys_config（系统配置）                                         │
│  sys_log（操作日志）                                            │
│  alert（告警）                                                  │
│  feedback（投诉建议）                                           │
│  gate（登机口）                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 状态枚举速查

### 订单状态（t_order.status）

| 状态 | 说明 |
|------|------|
| PENDING_PAYMENT | 待支付 |
| PAID | 已支付 |
| ISSUED | 已出票 |
| CHECKED_IN | 已值机 |
| BOARDING | 登机中 |
| DEPARTED | 已起飞 |
| ARRIVED | 已到达 |
| COMPLETED | 已完成 |
| CANCELLED | 已取消 |
| REFUNDING | 退票中 |
| REFUNDED | 已退票 |

### 航班状态（flight.status）

| 状态 | 说明 |
|------|------|
| SCHEDULED | 已计划 |
| CHECKING_IN | 值机中 |
| BOARDING | 登机中 |
| DEPARTED | 已起飞 |
| FLYING | 飞行中 |
| ARRIVED | 已到达 |
| DELAYED | 延误 |
| CANCELLED | 取消 |
| DIVERTED | 备降 |
| RETURNED | 返航 |

### 支付状态（payment.status）

| 状态 | 说明 |
|------|------|
| PENDING | 待支付 |
| SUCCESS | 支付成功 |
| FAILED | 支付失败 |

### 改签/退票状态（order_change.status / order_refund.status）

| 状态 | 说明 |
|------|------|
| PENDING | 待审核 |
| APPROVED | 已通过 |
| REJECTED | 已拒绝 |

### 机组状态（crew.status）

| 状态 | 说明 |
|------|------|
| FLYING | 飞行中 |
| STANDBY | 待命 |
| REST | 休息 |
| TRAINING | 培训 |
| LEAVE | 休假 |

### 座位状态（seat.status）

| 状态 | 说明 |
|------|------|
| AVAILABLE | 可选 |
| OCCUPIED | 已占用 |

### 登机口状态（gate.status）

| 状态 | 说明 |
|------|------|
| AVAILABLE | 可用 |
| OCCUPIED | 已占用 |

---

## 测试账号

### 旅客端用户

| 姓名 | 手机号 | 密码 | 会员等级 |
|------|--------|------|---------|
| 张三 | 13800138001 | Abc123456 | 金卡 |
| 李四 | 13800138002 | Abc123456 | 银卡 |
| 王五 | 13800138003 | Abc123456 | 白金卡 |
| 赵六 | 13800138004 | Abc123456 | 普通会员 |
| 孙七 | 13800138005 | Abc123456 | 金卡 |

### 管理端用户

| 姓名 | 用户名 | 密码 | 角色 |
|------|--------|------|------|
| 超级管理员 | admin | Admin@123 | 超级管理员 |
| 调度员张飞 | dispatcher01 | Admin@123 | 航班调度员 |
| 值机员李娜 | checkin01 | Admin@123 | 值机员 |
| 客服王芳 | cs01 | Admin@123 | 客服 |

### 短信验证码

开发环境固定验证码：`123456`

---

## 表统计汇总

| 分组 | 数量 | 表名 |
|------|:----:|------|
| 1. 系统管理相关表 | 6 | sys_user, sys_admin_user, sys_role, sys_permission, sys_user_role, sys_role_permission |
| 2. 基础数据表 | 5 | airport, airline, aircraft_type, aircraft, route |
| 3. 航班运营表 | 4 | flight, flight_cabin, flight_status_log, seat |
| 4. 订单与支付表 | 4 | t_order, order_passenger, order_service_item, payment |
| 5. 改签退票表 | 2 | order_change, order_refund |
| 6. 值机与登机表 | 2 | check_in, boarding_pass |
| 7. 机组管理表 | 3 | crew, crew_qualification, crew_schedule |
| 8. 会员相关表 | 3 | passenger_document, frequent_traveler, member_miles_record |
| 9. 航班动态与订阅 | 1 | flight_subscription |
| 10. 系统配置与日志 | 4 | sys_config, sys_log, alert, feedback, gate |
| 11. 新增表 | 7 | irregular_operation, auto_rebooking, special_service_request, ssr_code, ground_handling, crew_flight_time, notification_log |
| **合计** | **36** | |

---

*文档结束*
