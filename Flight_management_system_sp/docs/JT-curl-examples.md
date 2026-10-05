# 航班管理系统 - 接口请求示例（curl）

> 基础路径：`http://localhost:8080/api`
> 认证方式：`Authorization: Bearer <token>`
> 开发环境短信验证码固定为 `123456`，所有用户密码为 `Admin@123`

---

## 1. 旅客端接口

### 1.1 认证模块

#### 用户注册
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800138001",
    "smsCode": "123456",
    "password": "Admin@123",
    "name": "张三"
  }'
```

#### 用户登录
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800138001",
    "password": "Admin@123"
  }'
```

#### 短信验证码登录
```bash
curl -X POST http://localhost:8080/api/auth/login/sms \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800138001",
    "smsCode": "123456"
  }'
```

#### 发送验证码
```bash
curl -X POST http://localhost:8080/api/auth/sms/send \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800138001",
    "type": "register"
  }'
```

#### 刷新 Token
```bash
curl -X POST http://localhost:8080/api/auth/token/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }'
```

---

### 1.2 航班搜索模块

#### 搜索航班
```bash
curl -X GET "http://localhost:8080/api/flights/search?tripType=ONEWAY&departure=PEK&arrival=SHA&departDate=2026-06-10&adults=1&cabinClass=ECONOMY" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 航班详情
```bash
curl -X GET http://localhost:8080/api/flights/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 热门航线
```bash
# 返回各航线最低价航班信息（按价格升序），无需认证
curl -X GET "http://localhost:8080/api/flights/hot-routes?limit=10"
```

#### 特价机票
```bash
# 返回今日起最低价航班（按价格升序），无需认证
curl -X GET "http://localhost:8080/api/flights/deals?limit=10"
```

---

### 1.3 机票预订模块

#### 创建订单
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "searchId": "SR20260605001",
    "flightId": "1",
    "cabinClass": "ECONOMY",
    "passengers": [
      {
        "name": "张三",
        "gender": "MALE",
        "idType": "ID_CARD",
        "idNumber": "110101199001011234",
        "phone": "13800138001",
        "passengerType": "ADULT"
      }
    ],
    "contactInfo": {
      "name": "张三",
      "phone": "13800138001",
      "email": "zhangsan@example.com"
    },
    "services": [
      {
        "type": "BAGGAGE",
        "code": "BAG20",
        "quantity": 1,
        "passengerIndex": 0
      },
      {
        "type": "INSURANCE",
        "code": "INS_ALL",
        "quantity": 1,
        "passengerIndex": 0
      }
    ]
  }'
```

#### 查询订单详情
```bash
curl -X GET http://localhost:8080/api/orders/1001 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 订单列表
```bash
curl -X GET "http://localhost:8080/api/orders?status=PAID&page=1&pageSize=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 取消订单
```bash
curl -X POST http://localhost:8080/api/orders/1004/cancel \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "reason": "行程变更，无法出行"
  }'
```

#### 申请改签
```bash
curl -X POST http://localhost:8080/api/orders/1001/change \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "segmentIndex": 0,
    "newFlightId": "2",
    "newCabinClass": "ECONOMY",
    "passengers": [0]
  }'
```

#### 申请退票
```bash
curl -X POST http://localhost:8080/api/orders/1001/refund \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "reason": "行程变更，申请退票",
    "passengers": [0]
  }'
```

#### 查询改签状态
```bash
curl -X GET http://localhost:8080/api/orders/1001/change/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 查询退票状态
```bash
curl -X GET http://localhost:8080/api/orders/1006/refund/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

### 1.4 支付模块

#### 创建支付
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "orderId": "ORD20260605001",
    "payMethod": "WECHAT",
    "amount": 800.00
  }'
```

#### 查询支付状态
```bash
curl -X GET http://localhost:8080/api/payments/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

### 1.5 值机模块

#### 查询可值机航班
```bash
curl -X GET http://localhost:8080/api/checkin/available \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 获取座位图
```bash
curl -X GET http://localhost:8080/api/checkin/seats/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 选择座位
```bash
curl -X POST http://localhost:8080/api/checkin/seats \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "orderId": "1001",
    "passengerIndex": 0,
    "row": 12,
    "column": "A"
  }'
```

#### 办理值机
```bash
curl -X POST http://localhost:8080/api/checkin \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "orderId": "1001",
    "passengers": [
      {
        "passengerIndex": 0,
        "seatRow": 12,
        "seatColumn": "A"
      }
    ]
  }'
```

#### 获取电子登机牌
```bash
curl -X GET http://localhost:8080/api/checkin/boarding-pass/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

### 1.6 航班动态模块

#### 查询航班动态
```bash
curl -X GET "http://localhost:8080/api/flight-status?flightNo=CA1234&date=2026-06-10"
```

#### 订阅航班动态
```bash
curl -X POST http://localhost:8080/api/flight-status/subscribe \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "flightNo": "CA1234",
    "date": "2026-06-10",
    "channels": ["SMS", "PUSH"],
    "types": ["DELAY", "GATE_CHANGE", "BOARDING"]
  }'
```

#### 取消订阅
```bash
curl -X DELETE http://localhost:8080/api/flight-status/subscribe/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

### 1.7 会员中心模块

#### 获取个人信息
```bash
curl -X GET http://localhost:8080/api/member/profile \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 更新个人信息
```bash
curl -X PUT http://localhost:8080/api/member/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name": "张三丰",
    "gender": "MALE",
    "birthday": "1990-01-15",
    "email": "zhangsan@new.com"
  }'
```

#### 获取证件列表
```bash
curl -X GET http://localhost:8080/api/member/documents \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 添加证件
```bash
curl -X POST http://localhost:8080/api/member/documents \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "docType": "PASSPORT",
    "docNumber": "E12345678",
    "name": "张三",
    "nationality": "中国",
    "expireDate": "2028-06-01"
  }'
```

#### 更新证件
```bash
curl -X PUT http://localhost:8080/api/member/documents/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "expireDate": "2030-12-31"
  }'
```

#### 删除证件
```bash
curl -X DELETE http://localhost:8080/api/member/documents/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 获取常用旅客列表
```bash
curl -X GET http://localhost:8080/api/member/travelers \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 添加常用旅客
```bash
curl -X POST http://localhost:8080/api/member/travelers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name": "张小花",
    "gender": "FEMALE",
    "idType": "ID_CARD",
    "idNumber": "110101201508201234",
    "passengerType": "CHILD"
  }'
```

#### 更新常用旅客
```bash
curl -X PUT http://localhost:8080/api/member/travelers/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "phone": "13900139000"
  }'
```

#### 删除常用旅客
```bash
curl -X DELETE http://localhost:8080/api/member/travelers/1 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 里程查询
```bash
curl -X GET http://localhost:8080/api/member/miles \
  -H "Authorization: Bearer YOUR_TOKEN"
```

#### 里程兑换
```bash
curl -X POST http://localhost:8080/api/member/miles/redeem \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "type": "TICKET",
    "targetId": "1001",
    "miles": 1000
  }'
```

---

### 1.8 帮助中心模块

#### 获取 FAQ 列表
```bash
curl -X GET "http://localhost:8080/api/help/faq?category=BOOKING&keyword=预订"
```

#### 获取退改政策
```bash
curl -X GET http://localhost:8080/api/help/refund-policy
```

#### 获取行李规定
```bash
curl -X GET http://localhost:8080/api/help/baggage-rules
```

#### 提交投诉建议
```bash
curl -X POST http://localhost:8080/api/help/feedback \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "type": "COMPLAINT",
    "orderId": "1006",
    "content": "航班取消后退款迟迟未到账，请尽快处理。",
    "contactPhone": "13800138004"
  }'
```

---

### 1.9 文件上传模块

#### 上传单个文件
```bash
curl -X POST http://localhost:8080/api/file/upload \
  -F "file=@/path/to/image.jpg"
```

**响应示例：**
```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "url": "http://localhost:8080/api/images/2026/06/18/abc123.jpg",
    "path": "/images/2026/06/18/abc123.jpg",
    "filename": "image.jpg",
    "newFilename": "abc123.jpg",
    "fileType": "image",
    "extension": "jpg",
    "size": 102400
  }
}
```

#### 批量上传文件
```bash
curl -X POST http://localhost:8080/api/file/upload/batch \
  -F "files=@/path/to/image1.jpg" \
  -F "files=@/path/to/image2.png" \
  -F "files=@/path/to/video.mp4"
```

**响应示例：**
```json
{
  "code": 200,
  "message": "上传成功",
  "data": [
    {
      "url": "http://localhost:8080/api/images/2026/06/18/abc123.jpg",
      "path": "/images/2026/06/18/abc123.jpg",
      "filename": "image1.jpg",
      "newFilename": "abc123.jpg",
      "fileType": "image",
      "extension": "jpg",
      "size": 102400
    },
    {
      "url": "http://localhost:8080/api/images/2026/06/18/def456.mp4",
      "path": "/images/2026/06/18/def456.mp4",
      "filename": "video.mp4",
      "newFilename": "def456.mp4",
      "fileType": "video",
      "extension": "mp4",
      "size": 5242880
    }
  ]
}
```

#### 访问上传的文件
```bash
# 直接通过浏览器访问
curl -X GET http://localhost:8080/api/images/2026/06/18/abc123.jpg
```

---

## 2. 管理端接口

### 2.1 管理员认证

#### 管理员登录
```bash
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin@123"
  }'
```

#### 管理员登出
```bash
curl -X POST http://localhost:8080/api/admin/auth/logout \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.2 航班调度模块

#### 航班计划列表
```bash
curl -X GET "http://localhost:8080/api/admin/flights?date=2026-06-10&page=1&pageSize=20" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建航班
```bash
curl -X POST http://localhost:8080/api/admin/flights \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "flightNo": "CA8888",
    "date": "2026-06-15",
    "route": {
      "departure": "PEK",
      "arrival": "CAN"
    },
    "schedule": {
      "departureTime": "08:00",
      "arrivalTime": "11:10"
    },
    "aircraft": {
      "type": "B738",
      "registration": "B-1234"
    },
    "flightType": "DOMESTIC",
    "stops": [],
    "remark": "新增航班"
  }'
```

#### 编辑航班
```bash
curl -X PUT http://localhost:8080/api/admin/flights/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "flightNo": "CA1234",
    "date": "2026-06-10",
    "flightType": "DOMESTIC",
    "remark": "已更新备注"
  }'
```

#### 删除航班
```bash
curl -X DELETE http://localhost:8080/api/admin/flights/11 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 批量操作
```bash
curl -X POST http://localhost:8080/api/admin/flights/batch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "flightIds": [9, 10],
    "action": "CANCEL",
    "params": {}
  }'
```

#### 航班时刻表
```bash
curl -X GET "http://localhost:8080/api/admin/flights/schedule?view=CALENDAR&startDate=2026-06-10&endDate=2026-06-16" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 不正常航班处理
```bash
curl -X POST http://localhost:8080/api/admin/flights/1/irregular \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "type": "DELAY",
    "reason": "流量控制",
    "newSchedule": {
      "departureTime": "2026-06-10T08:00:00"
    },
    "notifyPassengers": true,
    "arrangements": {
      "meal": true,
      "accommodation": false,
      "transportation": true
    }
  }'
```

#### 航班日志
```bash
curl -X GET http://localhost:8080/api/admin/flights/1/logs \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.3 旅客服务模块

#### 获取航班值机信息
```bash
curl -X GET http://localhost:8080/api/admin/checkin/1 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 开放值机
```bash
curl -X POST http://localhost:8080/api/admin/checkin/1/open \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 关闭值机
```bash
curl -X POST http://localhost:8080/api/admin/checkin/1/close \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 手动值机
```bash
curl -X POST http://localhost:8080/api/admin/checkin/1/checkin \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "passengerIndex": 0,
    "seatRow": 15,
    "seatColumn": "A"
  }'
```

#### 取消值机
```bash
curl -X POST http://localhost:8080/api/admin/checkin/1/cancel-checkin \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "passengerIndex": 0
  }'
```

#### 自动分配座位
```bash
curl -X POST http://localhost:8080/api/admin/checkin/1/auto-assign \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 导出值机名单
```bash
curl -X GET http://localhost:8080/api/admin/checkin/1/export \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 获取登机口列表
```bash
curl -X GET "http://localhost:8080/api/admin/gates?terminal=T3&date=2026-06-10" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 分配登机口
```bash
curl -X POST http://localhost:8080/api/admin/gates/assign \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "flightId": 1,
    "gate": "A12",
    "terminal": "T3",
    "startTime": "2026-06-10T06:30:00",
    "endTime": "2026-06-10T08:00:00"
  }'
```

#### 变更登机口
```bash
curl -X PUT http://localhost:8080/api/admin/gates/change \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "flightId": 1,
    "gate": "A15"
  }'
```

#### 登机状态
```bash
curl -X GET "http://localhost:8080/api/admin/gates/board?date=2026-06-10" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 获取航班座位图
```bash
curl -X GET http://localhost:8080/api/admin/seats/1 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 锁定座位
```bash
curl -X POST http://localhost:8080/api/admin/seats/1/lock \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "row": 1,
    "column": "A"
  }'
```

#### 解锁座位
```bash
curl -X POST http://localhost:8080/api/admin/seats/1/unlock \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "row": 1,
    "column": "A"
  }'
```

#### 旅客查询
```bash
curl -X GET "http://localhost:8080/api/admin/passengers?name=张三&flightNo=CA1234" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.4 机组管理模块

#### 机组排班查询
```bash
curl -X GET "http://localhost:8080/api/admin/crew/schedule?startDate=2026-06-10&endDate=2026-06-16" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建排班
```bash
curl -X POST http://localhost:8080/api/admin/crew/schedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "crewId": 1,
    "flightId": 1,
    "role": "CAPTAIN",
    "date": "2026-06-10"
  }'
```

#### 自动排班
```bash
curl -X POST http://localhost:8080/api/admin/crew/schedule/auto \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "startDate": "2026-06-10",
    "endDate": "2026-06-16",
    "flights": ["1", "2", "3"]
  }'
```

#### 删除排班
```bash
curl -X DELETE http://localhost:8080/api/admin/crew/schedule/1 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 获取机组资质
```bash
curl -X GET http://localhost:8080/api/admin/crew/1/qualifications \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 添加资质
```bash
curl -X POST http://localhost:8080/api/admin/crew/1/qualifications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "type": "AIRCRAFT",
    "name": "A320机长资质",
    "number": "AC20240001",
    "level": "CAPTAIN"
  }'
```

#### 更新资质
```bash
curl -X PUT http://localhost:8080/api/admin/crew/1/qualifications/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "status": "EXPIRING_SOON"
  }'
```

#### 机组动态
```bash
curl -X GET "http://localhost:8080/api/admin/crew/dynamics?status=STANDBY" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.5 运营监控模块

#### 监控大屏数据
```bash
curl -X GET http://localhost:8080/api/admin/monitor/dashboard \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 告警中心
```bash
curl -X GET "http://localhost:8080/api/admin/alerts?level=URGENT&status=PENDING" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 处理告警
```bash
curl -X POST http://localhost:8080/api/admin/alerts/1/resolve \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 统计概览
```bash
curl -X GET "http://localhost:8080/api/admin/statistics?dimension=FLIGHT&startDate=2026-06-01&endDate=2026-06-30&groupBy=DAY" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.6 客票管理模块

#### 订座管理
```bash
curl -X GET "http://localhost:8080/api/admin/tickets/bookings?pnr=ABC123" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 订座详情
```bash
curl -X GET http://localhost:8080/api/admin/tickets/bookings/ABC123 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 运价列表
```bash
curl -X GET http://localhost:8080/api/admin/tickets/fares \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建运价
```bash
curl -X POST http://localhost:8080/api/admin/tickets/fares \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "routeId": 1,
    "cabinClass": "ECONOMY",
    "baseFare": 580,
    "tax": 90
  }'
```

#### 更新运价
```bash
curl -X PUT http://localhost:8080/api/admin/tickets/fares/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "baseFare": 600
  }'
```

#### 退票列表
```bash
curl -X GET http://localhost:8080/api/admin/tickets/refunds \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 审核退票
```bash
curl -X POST http://localhost:8080/api/admin/tickets/refunds/1/approve \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 改签列表
```bash
curl -X GET http://localhost:8080/api/admin/tickets/changes \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 审核改签
```bash
curl -X POST http://localhost:8080/api/admin/tickets/changes/1/approve \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.7 报表中心模块

#### 运营报表
```bash
curl -X GET "http://localhost:8080/api/admin/reports/operation?type=DAILY&startDate=2026-06-01&endDate=2026-06-30&format=JSON" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 收入报表
```bash
curl -X GET "http://localhost:8080/api/admin/reports/revenue?type=ROUTE&startDate=2026-06-01&endDate=2026-06-30" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 自定义报表列表
```bash
curl -X GET http://localhost:8080/api/admin/reports/custom \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建自定义报表
```bash
curl -X POST http://localhost:8080/api/admin/reports/custom \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "name": "月度运营汇总",
    "dimension": "FLIGHT",
    "dateRange": "2026-06-01~2026-06-30"
  }'
```

#### 生成报表
```bash
curl -X POST http://localhost:8080/api/admin/reports/custom/1/generate \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.8 系统管理模块

#### 用户列表
```bash
curl -X GET "http://localhost:8080/api/admin/system/users?page=1&pageSize=20" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建用户
```bash
curl -X POST http://localhost:8080/api/admin/system/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "username": "dispatcher02",
    "name": "调度员02",
    "phone": "13900139010",
    "email": "dispatch02@airline.com",
    "roles": ["DISPATCHER"],
    "password": "Init@12345"
  }'
```

#### 更新用户
```bash
curl -X PUT http://localhost:8080/api/admin/system/users/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "name": "调度员张飞（已更新）",
    "email": "zhangfei@new.com"
  }'
```

#### 禁用用户
```bash
curl -X POST http://localhost:8080/api/admin/system/users/3/disable \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 启用用户
```bash
curl -X POST http://localhost:8080/api/admin/system/users/3/enable \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 重置密码
```bash
curl -X POST http://localhost:8080/api/admin/system/users/2/reset-password \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 角色列表
```bash
curl -X GET http://localhost:8080/api/admin/system/roles \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建角色
```bash
curl -X POST http://localhost:8080/api/admin/system/roles \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "name": "数据分析师",
    "code": "ANALYST",
    "permissions": ["flight:read", "report:read", "monitor:read"],
    "description": "数据分析师角色"
  }'
```

#### 更新角色
```bash
curl -X PUT http://localhost:8080/api/admin/system/roles/5 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "name": "高级数据分析师",
    "description": "拥有更多数据权限"
  }'
```

#### 删除角色
```bash
curl -X DELETE http://localhost:8080/api/admin/system/roles/5 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 获取配置
```bash
curl -X GET http://localhost:8080/api/admin/system/config \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 更新配置
```bash
curl -X PUT http://localhost:8080/api/admin/system/config \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "checkin_open_hours": "{\"value\": 24}",
    "boarding_close_minutes": "{\"value\": 20}"
  }'
```

#### 操作日志
```bash
curl -X GET "http://localhost:8080/api/admin/system/logs?module=航班管理&page=1&pageSize=20" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.9 基础数据管理

#### 机场列表
```bash
curl -X GET http://localhost:8080/api/admin/system/master-data/airports \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建机场
```bash
curl -X POST http://localhost:8080/api/admin/system/master-data/airports \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "code": "CKG",
    "name": "重庆江北国际机场",
    "city": "重庆",
    "province": "重庆",
    "country": "中国"
  }'
```

#### 更新机场
```bash
curl -X PUT http://localhost:8080/api/admin/system/master-data/airports/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "name": "北京首都国际机场（更新）"
  }'
```

#### 删除机场
```bash
curl -X DELETE http://localhost:8080/api/admin/system/master-data/airports/11 \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 航线列表
```bash
curl -X GET http://localhost:8080/api/admin/system/master-data/routes \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 机型列表
```bash
curl -X GET http://localhost:8080/api/admin/system/master-data/aircraft-types \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建机型
```bash
curl -X POST http://localhost:8080/api/admin/system/master-data/aircraft-types \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "code": "A321",
    "name": "空客A321neo",
    "manufacturer": "空客",
    "totalSeats": 220
  }'
```

#### 机队列表
```bash
curl -X GET http://localhost:8080/api/admin/system/master-data/fleet \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### 创建飞机
```bash
curl -X POST http://localhost:8080/api/admin/system/master-data/fleet \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -d '{
    "registration": "B-0001",
    "aircraftTypeId": 1,
    "airlineId": 1
  }'
```

#### 部门列表
```bash
curl -X GET http://localhost:8080/api/admin/system/master-data/departments \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

### 2.10 不正常航班(IROPS) - 管理端

```bash
# 查询不正常航班列表
curl -X GET "http://localhost:8080/api/admin/irop/operations?page=1&pageSize=10&type=DELAY" \
  -H "Authorization: Bearer <token>"

# 查询受影响旅客
curl -X GET "http://localhost:8080/api/admin/irop/passengers/1" \
  -H "Authorization: Bearer <token>"

# 查询改签结果列表
curl -X GET "http://localhost:8080/api/admin/irop/rebookings?flightId=1&status=PENDING" \
  -H "Authorization: Bearer <token>"

# 确认/拒绝自动改签
curl -X POST "http://localhost:8080/api/admin/irop/rebookings/1/confirm" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"action": "APPROVED"}'

# 手动改签
curl -X POST "http://localhost:8080/api/admin/irop/manual-rebook" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "newFlightId": 2, "newCabinClass": "Y"}'

# 查询补偿规则
curl -X GET "http://localhost:8080/api/admin/irop/compensation-rules" \
  -H "Authorization: Bearer <token>"

# 查询IATA延误代码
curl -X GET "http://localhost:8080/api/admin/irop/delay-codes" \
  -H "Authorization: Bearer <token>"
```

---

### 2.11 特殊旅客服务(SSR) - 管理端

```bash
# 获取SSR代码列表
curl -X GET "http://localhost:8080/api/admin/ssr/codes?category=wheelchair" \
  -H "Authorization: Bearer <token>"

# 查询SSR请求列表
curl -X GET "http://localhost:8080/api/admin/ssr/requests?page=1&pageSize=10&flightId=1&status=PENDING" \
  -H "Authorization: Bearer <token>"

# 提交SSR请求
curl -X POST "http://localhost:8080/api/admin/ssr/submit" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "orderPassengerId": 1, "ssrCode": "WCHR", "remark": "右腿骨折"}'

# 处理SSR请求
curl -X POST "http://localhost:8080/api/admin/ssr/requests/1/process" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"action": "APPROVED", "remark": "已安排"}'

# 查询订单SSR
curl -X GET "http://localhost:8080/api/admin/ssr/orders/1" \
  -H "Authorization: Bearer <token>"
```

---

### 2.12 地面保障 - 管理端

```bash
# 今日保障概览
curl -X GET "http://localhost:8080/api/admin/ground/today" \
  -H "Authorization: Bearer <token>"

# 查询航班保障节点
curl -X GET "http://localhost:8080/api/admin/ground/nodes/1" \
  -H "Authorization: Bearer <token>"

# 查询保障记录（分页）
curl -X GET "http://localhost:8080/api/admin/ground?page=1&pageSize=10&flightNo=CZ3101&date=2026-06-24" \
  -H "Authorization: Bearer <token>"

# 更新节点时间
curl -X PUT "http://localhost:8080/api/admin/ground/nodes/1/time" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"field": "actualTime", "value": "2026-06-24T10:30:00"}'
```

---

### 2.13 机组合规 - 管理端

```bash
# 合规看板
curl -X GET "http://localhost:8080/api/admin/crew-compliance/dashboard" \
  -H "Authorization: Bearer <token>"

# 机组飞行时间汇总
curl -X GET "http://localhost:8080/api/admin/crew-compliance/summary/1?days=30" \
  -H "Authorization: Bearer <token>"

# 合规预警列表
curl -X GET "http://localhost:8080/api/admin/crew-compliance/warnings" \
  -H "Authorization: Bearer <token>"
```

---

### 新增WebSocket端点

```bash
# 旅客端通知WebSocket
wscat -c "ws://localhost:8080/api/ws/passenger/notification?token=<token>&userId=1"

# 管理端IROPS WebSocket
wscat -c "ws://localhost:8080/api/admin/ws/irregular?token=<token>"
```

---

## 3. WebSocket 连接

### 航班动态推送
```javascript
// 连接
const ws = new WebSocket('ws://localhost:8080/api/ws/flight-status?token=YOUR_TOKEN');

// 订阅
ws.onopen = () => {
  ws.send(JSON.stringify({
    action: "SUBSCRIBE",
    flights: ["CA1234", "MU5678"]
  }));
};

// 接收推送
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log(data);
};
```

### 管理端监控推送
```javascript
const ws = new WebSocket('ws://localhost:8080/api/admin/ws/monitor?token=ADMIN_TOKEN');

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  // data.type: FLIGHT_STATUS / ALERT / DASHBOARD_UPDATE / CHECKIN_UPDATE
  console.log(data.type, data.data);
};
```
