# 航班管理系统 - 接口文档 (API Documentation)

> 版本：v1.0  
> 日期：2026-06-05      
> 项目代号：SkyTrip / SkyOps  
> 文档编号：JT - 接口文档

---

## 1. 文档说明

### 1.1 接口规范

| 项目 | 说明 |
|------|------|
| 基础路径 | `https://api.skytrip.com/v1` |
| 协议 | HTTPS |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |
| 认证方式 | Bearer Token (JWT) |
| 时间格式 | ISO 8601 (YYYY-MM-DDTHH:mm:ssZ) |

### 1.2 通用响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-06-05T10:30:00Z",
  "requestId": "req_abc123def456"
}
```

### 1.3 错误码定义

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权/Token过期 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 429 | 请求频率过高 |
| 500 | 服务器内部错误 |
| 10001 | 航班不存在 |
| 10002 | 航班已满 |
| 10003 | 座位已被占用 |
| 10004 | 订单已过期 |
| 10005 | 支付失败 |
| 10006 | 退改签规则不允许 |
| 20001 | 用户不存在 |
| 20002 | 密码错误 |
| 20003 | 验证码错误 |
| 20004 | 证件信息已存在 |
| 30001 | 机组资质不满足 |
| 30002 | 排班冲突 |
| 30003 | 飞行时限超限 |

### 1.4 分页参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页条数，默认20，最大100 |
| sortBy | string | 否 | 排序字段 |
| sortOrder | string | 否 | asc/desc |

**分页响应结构：**
```json
{
  "code": 200,
  "data": {
    "list": [],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "total": 100,
      "totalPages": 5
    }
  }
}
```

---

## 2. 旅客端接口

### 2.1 用户认证模块

#### 2.1.1 用户注册

**POST** `/auth/register`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | string | 是 | 手机号 |
| smsCode | string | 是 | 短信验证码 |
| password | string | 是 | 密码（8-20位，含字母和数字） |
| name | string | 否 | 用户姓名 |

**请求示例：**
```json
{
  "phone": "13800138000",
  "smsCode": "123456",
  "password": "Abc123456",
  "name": "张三"
}
```

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "userId": "U20260605001",
    "phone": "138****8000",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "rt_abc123...",
    "expiresIn": 7200
  }
}
```

#### 2.1.2 用户登录

**POST** `/auth/login`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | string | 是 | 手机号 |
| password | string | 是 | 登录密码 |
| captcha | string | 否 | 验证码（触发风控时必填） |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "userId": "U20260605001",
    "name": "张三",
    "phone": "138****8000",
    "avatar": "https://cdn.skytrip.com/avatar/default.png",
    "memberLevel": "GOLD",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "rt_abc123...",
    "expiresIn": 7200
  }
}
```

#### 2.1.3 短信验证码登录

**POST** `/auth/login/sms`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | string | 是 | 手机号 |
| smsCode | string | 是 | 短信验证码 |

#### 2.1.4 发送验证码

**POST** `/auth/sms/send`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phone | string | 是 | 手机号 |
| type | string | 是 | register/login/resetPassword |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "expireIn": 300
  }
}
```

#### 2.1.5 第三方登录

**GET** `/auth/oauth/{provider}`

**路径参数：**

| 参数 | 说明 |
|------|------|
| provider | wechat/alipay |

**响应：** 重定向至第三方授权页面

#### 2.1.6 刷新Token

**POST** `/auth/token/refresh`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refreshToken | string | 是 | 刷新令牌 |

---

### 2.2 航班搜索模块

#### 2.2.1 搜索航班

**GET** `/flights/search`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tripType | string | 是 | ONEWAY/ROUND/MULTI |
| departure | string | 是 | 出发城市/机场代码 |
| arrival | string | 是 | 到达城市/机场代码 |
| departDate | string | 否 | 出发日期 YYYY-MM-DD，为空时查询该航线所有日期航班 |
| returnDate | string | 否 | 返程日期（往返必填） |
| adults | int | 是 | 成人人数（1-9） |
| children | int | 否 | 儿童人数（0-9） |
| infants | int | 否 | 婴儿人数（0-9） |
| cabinClass | string | 否 | ECONOMY/BUSINESS/FIRST，默认ECONOMY |
| directOnly | boolean | 否 | 是否仅直飞，默认false |
| sortBy | string | 否 | RECOMMEND/PRICE/TIME/DURATION |
| sortOrder | string | 否 | asc/desc |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "searchId": "SR20260605001",
    "flights": [
      {
        "flightId": "FL20260610001",
        "flightNo": "CA1234",
        "airline": {
          "code": "CA",
          "name": "中国国际航空",
          "logo": "https://cdn.skytrip.com/airline/ca.png"
        },
        "departure": {
          "airport": "PEK",
          "airportName": "北京首都国际机场",
          "terminal": "T3",
          "city": "北京",
          "dateTime": "2026-06-10T07:30:00+08:00"
        },
        "arrival": {
          "airport": "SHA",
          "airportName": "上海虹桥国际机场",
          "terminal": "T2",
          "city": "上海",
          "dateTime": "2026-06-10T09:45:00+08:00"
        },
        "duration": 135,
        "aircraft": {
          "type": "B738",
          "name": "波音737-800"
        },
        "stops": 0,
        "punctuality": 92,
        "cabins": [
          {
            "class": "ECONOMY",
            "className": "经济舱",
            "fare": 580,
            "tax": 90,
            "totalPrice": 670,
            "seats": 28,
            "baggage": "20kg",
            "refundRule": "起飞前2小时，手续费10%",
            "changeRule": "起飞前2小时，手续费5%"
          }
        ],
        "services": {
          "wifi": true,
          "meal": true,
          "power": true
        }
      }
    ],
    "priceCalendar": {
      "2026-06-09": 720,
      "2026-06-10": 670,
      "2026-06-11": 580,
      "2026-06-12": 550,
      "2026-06-13": 620
    }
  }
}
```

#### 2.2.2 航班详情

**GET** `/flights/{flightId}`

**路径参数：**

| 参数 | 说明 |
|------|------|
| flightId | 航班ID |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "flightId": "FL20260610001",
    "flightNo": "CA1234",
    "airline": {
      "code": "CA",
      "name": "中国国际航空"
    },
    "departure": {
      "airport": "PEK",
      "airportName": "北京首都国际机场",
      "terminal": "T3",
      "gate": "A12",
      "city": "北京",
      "dateTime": "2026-06-10T07:30:00+08:00"
    },
    "arrival": {
      "airport": "SHA",
      "airportName": "上海虹桥国际机场",
      "terminal": "T2",
      "city": "上海",
      "dateTime": "2026-06-10T09:45:00+08:00"
    },
    "duration": 135,
    "aircraft": {
      "type": "B738",
      "name": "波音737-800",
      "seatLayout": "3-3",
      "totalSeats": 162
    },
    "punctuality": {
      "rate": 92,
      "sampleDays": 30
    },
    "baggage": {
      "free": "20kg",
      "excessFee": "每公斤按经济舱全价票的1.5%"
    },
    "meal": {
      "provided": true,
      "type": "正餐",
      "special": ["素食", "清真", "低脂"]
    },
    "cabins": [
      {
        "class": "ECONOMY",
        "className": "经济舱",
        "fare": 580,
        "tax": 90,
        "totalPrice": 670,
        "seats": 28,
        "refundRule": {
          "before2h": "手续费10%",
          "after2h": "不可退票"
        },
        "changeRule": {
          "before2h": "手续费5%",
          "after2h": "不可改签"
        }
      },
      {
        "class": "BUSINESS",
        "className": "公务舱",
        "fare": 2800,
        "tax": 90,
        "totalPrice": 2890,
        "seats": 8,
        "refundRule": {
          "before2h": "手续费5%",
          "after2h": "手续费10%"
        },
        "changeRule": {
          "before2h": "免费改签",
          "after2h": "手续费5%"
        }
      }
    ]
  }
}
```

#### 2.2.3 热门航线

**GET** `/flights/hot-routes`

> 无需认证

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| city | string | 否 | 出发城市（暂未启用，保留字段） |
| limit | int | 否 | 返回数量，默认10 |

**响应示例：**
```json
{
  "code": 200,
  "data": [
    {
      "flightId": "1",
      "departure": {
        "code": "PEK",
        "city": "北京"
      },
      "arrival": {
        "code": "SHA",
        "city": "上海"
      },
      "price": 580,
      "date": "2026-06-10"
    },
    {
      "flightId": "5",
      "departure": {
        "code": "PEK",
        "city": "北京"
      },
      "arrival": {
        "code": "CAN",
        "city": "广州"
      },
      "price": 720,
      "date": "2026-06-11"
    }
  ]
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| flightId | string | 该航线最低价航班的ID |
| departure.code | string | 出发机场代码 |
| departure.city | string | 出发城市名 |
| arrival.code | string | 到达机场代码 |
| arrival.city | string | 到达城市名 |
| price | number | 该航线最低票价（元） |
| date | string | 航班日期 YYYY-MM-DD |

#### 2.2.4 特价机票

**GET** `/flights/deals`

> 无需认证

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| city | string | 否 | 出发城市（暂未启用，保留字段） |
| limit | int | 否 | 返回数量，默认10 |

**响应示例：**
```json
{
  "code": 200,
  "data": [
    {
      "flightId": "3",
      "departure": {
        "code": "PEK",
        "city": "北京"
      },
      "arrival": {
        "code": "SHA",
        "city": "上海"
      },
      "price": 420,
      "date": "2026-06-12",
      "airline": "天行航空"
    },
    {
      "flightId": "7",
      "departure": {
        "code": "CAN",
        "city": "广州"
      },
      "arrival": {
        "code": "CTU",
        "city": "成都"
      },
      "price": 380,
      "date": "2026-06-13",
      "airline": "天行航空"
    }
  ]
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| flightId | string | 航班ID |
| departure.code | string | 出发机场代码 |
| departure.city | string | 出发城市名 |
| arrival.code | string | 到达机场代码 |
| arrival.city | string | 到达城市名 |
| price | number | 最低票价（元） |
| date | string | 航班日期 YYYY-MM-DD |
| airline | string | 航空公司名称 |

---

### 2.3 机票预订模块

#### 2.3.1 创建订单

**POST** `/orders`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| searchId | string | 是 | 搜索ID |
| flightId | string | 是 | 航班ID |
| cabinClass | string | 是 | 舱位等级 |
| passengers | array | 是 | 旅客列表 |
| contactInfo | object | 是 | 联系人信息 |
| services | array | 否 | 附加服务列表 |

**passengers 数组项：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 姓名 |
| gender | string | 是 | MALE/FEMALE |
| birthday | string | 否 | 出生日期（儿童/婴儿必填） |
| idType | string | 是 | ID_CARD/PASSPORT/HM_PASSPORT/TAIWAN_PASSPORT/OTHER |
| idNumber | string | 是 | 证件号码 |
| phone | string | 否 | 手机号 |
| email | string | 否 | 邮箱 |
| frequentFlyerNo | string | 否 | 常旅客号 |
| passengerType | string | 是 | ADULT/CHILD/INFANT |

**contactInfo：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 联系人姓名 |
| phone | string | 是 | 联系电话 |
| email | string | 否 | 邮箱 |

**services 数组项：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | BAGGAGE/INSURANCE/SEAT/MEAL/LOUNGE |
| code | string | 是 | 服务代码 |
| quantity | int | 是 | 数量 |
| passengerIndex | int | 是 | 关联旅客索引 |

**请求示例：**
```json
{
  "searchId": "SR20260605001",
  "flightId": "FL20260610001",
  "cabinClass": "ECONOMY",
  "passengers": [
    {
      "name": "张三",
      "gender": "MALE",
      "idType": "ID_CARD",
      "idNumber": "110101199001011234",
      "phone": "13800138000",
      "passengerType": "ADULT"
    }
  ],
  "contactInfo": {
    "name": "张三",
    "phone": "13800138000",
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
}
```

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "orderId": "ORD20260605001",
    "orderNo": "ST20260605001",
    "pnr": "ABC123",
    "status": "PENDING_PAYMENT",
    "expireAt": "2026-06-05T10:45:00Z",
    "flight": {
      "flightNo": "CA1234",
      "departure": {
        "airport": "PEK",
        "terminal": "T3",
        "dateTime": "2026-06-10T07:30:00+08:00"
      },
      "arrival": {
        "airport": "SHA",
        "terminal": "T2",
        "dateTime": "2026-06-10T09:45:00+08:00"
      }
    },
    "passengers": [
      {
        "name": "张三",
        "ticketNo": "999-1234567890"
      }
    ],
    "price": {
      "fare": 580,
      "tax": 90,
      "services": 130,
      "total": 800
    }
  }
}
```

#### 2.3.2 查询订单详情

**GET** `/orders/{orderId}`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "orderId": "ORD20260605001",
    "orderNo": "ST20260605001",
    "pnr": "ABC123",
    "status": "PAID",
    "createdAt": "2026-06-05T10:30:00Z",
    "paidAt": "2026-06-05T10:35:00Z",
    "flight": {
      "flightId": "FL20260610001",
      "flightNo": "CA1234",
      "airline": {
        "code": "CA",
        "name": "中国国际航空"
      },
      "departure": {
        "airport": "PEK",
        "airportName": "北京首都国际机场",
        "terminal": "T3",
        "gate": "A12",
        "dateTime": "2026-06-10T07:30:00+08:00"
      },
      "arrival": {
        "airport": "SHA",
        "airportName": "上海虹桥国际机场",
        "terminal": "T2",
        "dateTime": "2026-06-10T09:45:00+08:00"
      },
      "status": "SCHEDULED"
    },
    "passengers": [
      {
        "name": "张三",
        "idType": "ID_CARD",
        "idNumber": "110***********1234",
        "ticketNo": "999-1234567890",
        "seat": "12A",
        "checkinStatus": "NOT_CHECKED_IN"
      }
    ],
    "price": {
      "fare": 580,
      "tax": 90,
      "insurance": 30,
      "baggage": 100,
      "total": 800
    },
    "refundChangeRules": {
      "refund": "起飞前2小时，手续费10%；起飞后不可退",
      "change": "起飞前2小时，手续费5%",
      "transfer": "不可签转"
    },
    "actions": ["CHECKIN", "CHANGE", "REFUND", "INVOICE"]
  }
}
```

**新增Order字段：** `rebookedFromId`（改签来源订单ID）、`ssrFlag`（特殊服务标识）

---

#### 2.3.3 订单列表

**GET** `/orders`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 否 | PENDING_PAYMENT/PAID/COMPLETED/CANCELLED |
| startDate | string | 否 | 开始日期 |
| endDate | string | 否 | 结束日期 |
| keyword | string | 否 | 关键词（航班号/订单号） |

#### 2.3.4 取消订单

**POST** `/orders/{orderId}/cancel`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| reason | string | 否 | 取消原因 |

---

### 2.4 支付模块

#### 2.4.1 创建支付

**POST** `/payments`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderId | string | 是 | 订单ID |
| payMethod | string | 是 | WECHAT/ALIPAY/BANK_CARD/CREDIT |
| amount | number | 是 | 支付金额 |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "paymentId": "PAY20260605001",
    "payUrl": "https://pay.weixin.qq.com/...",
    "qrCode": "weixin://wxpay/...",
    "expireAt": "2026-06-05T10:45:00Z"
  }
}
```

#### 2.4.2 查询支付状态

**GET** `/payments/{paymentId}`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "paymentId": "PAY20260605001",
    "orderId": "ORD20260605001",
    "amount": 800,
    "status": "SUCCESS",
    "paidAt": "2026-06-05T10:35:00Z",
    "transactionId": "WX20260605001"
  }
}
```

#### 2.4.3 支付回调

**POST** `/payments/callback/{provider}`

> 此接口由支付平台调用，非客户端调用

---

### 2.5 改签退票模块

#### 2.5.1 申请改签

**POST** `/orders/{orderId}/change`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| segmentIndex | int | 是 | 改签航段索引 |
| newFlightId | string | 是 | 新航班ID |
| newCabinClass | string | 是 | 新舱位等级 |
| passengers | array | 否 | 改签旅客（部分改签时） |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "changeId": "CHG20260605001",
    "status": "PENDING",
    "originalFlight": {
      "flightNo": "CA1234",
      "dateTime": "2026-06-10T07:30:00+08:00"
    },
    "newFlight": {
      "flightNo": "CA1236",
      "dateTime": "2026-06-10T09:00:00+08:00"
    },
    "fee": {
      "changeFee": 33.5,
      "fareDiff": 50,
      "total": 83.5
    }
  }
}
```

#### 2.5.2 申请退票

**POST** `/orders/{orderId}/refund`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| reason | string | 是 | 退票原因 |
| passengers | array | 否 | 退票旅客（部分退票时） |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "refundId": "REF20260605001",
    "status": "PENDING",
    "refundAmount": 612,
    "refundFee": 68,
    "refundAccount": "原路返回",
    "estimatedArrival": "1-7个工作日"
  }
}
```

#### 2.5.3 查询改签/退票状态

**GET** `/orders/{orderId}/change/{changeId}`

**GET** `/orders/{orderId}/refund/{refundId}`

---

### 2.6 在线值机模块

#### 2.6.1 查询可值机航班

**GET** `/checkin/available`

**响应示例：**
```json
{
  "code": 200,
  "data": [
    {
      "orderId": "ORD20260605001",
      "flightNo": "CA1234",
      "departure": {
        "airport": "PEK",
        "terminal": "T3",
        "dateTime": "2026-06-10T07:30:00+08:00"
      },
      "checkinOpenAt": "2026-06-09T07:30:00+08:00",
      "checkinCloseAt": "2026-06-10T06:30:00+08:00",
      "passengers": [
        {
          "name": "张三",
          "checkedIn": false
        }
      ]
    }
  ]
}
```

#### 2.6.2 获取座位图

**GET** `/checkin/seats/{flightId}`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "flightId": "FL20260610001",
    "aircraft": "B738",
    "layout": {
      "rows": 28,
      "columns": ["A", "B", "C", "D", "E", "F"],
      "exitRows": [1, 15]
    },
    "cabins": [
      {
        "class": "BUSINESS",
        "startRow": 1,
        "endRow": 3
      },
      {
        "class": "ECONOMY",
        "startRow": 4,
        "endRow": 28
      }
    ],
    "seats": [
      {
        "row": 1,
        "column": "A",
        "status": "AVAILABLE",
        "class": "BUSINESS",
        "type": "WINDOW",
        "extra": false
      },
      {
        "row": 1,
        "column": "B",
        "status": "OCCUPIED",
        "class": "BUSINESS",
        "type": "MIDDLE"
      },
      {
        "row": 12,
        "column": "A",
        "status": "AVAILABLE",
        "class": "ECONOMY",
        "type": "WINDOW",
        "extra": true,
        "extraFee": 50
      }
    ]
  }
}
```

#### 2.6.3 选择座位

**POST** `/checkin/seats`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderId | string | 是 | 订单ID |
| passengerIndex | int | 是 | 旅客索引 |
| row | int | 是 | 排号 |
| column | string | 是 | 列号 |

#### 2.6.4 办理值机

**POST** `/checkin`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderId | string | 是 | 订单ID |
| passengers | array | 是 | 值机旅客列表 |

**passengers 数组项：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| passengerIndex | int | 是 | 旅客索引 |
| seatRow | int | 是 | 座位排号 |
| seatColumn | string | 是 | 座位列号 |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "checkinId": "CHK20260605001",
    "boardingPasses": [
      {
        "passenger": "张三",
        "flightNo": "CA1234",
        "seat": "12A",
        "gate": "A12",
        "boardingTime": "2026-06-10T07:00:00+08:00",
        "qrCode": "https://cdn.skytrip.com/boardingpass/qr/xxx.png",
        "barcode": "M1SAN/ZHANG   E1234567890"
      }
    ]
  }
}
```

#### 2.6.5 获取电子登机牌

**GET** `/checkin/boarding-pass/{checkinId}`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "checkinId": "CHK20260605001",
    "passenger": {
      "name": "张三",
      "idNumber": "110***********1234"
    },
    "flight": {
      "flightNo": "CA1234",
      "departure": {
        "airport": "PEK",
        "airportName": "北京首都国际机场",
        "terminal": "T3",
        "gate": "A12"
      },
      "arrival": {
        "airport": "SHA",
        "airportName": "上海虹桥国际机场",
        "terminal": "T2"
      },
      "dateTime": "2026-06-10T07:30:00+08:00"
    },
    "seat": "12A",
    "boardingTime": "2026-06-10T07:00:00+08:00",
    "qrCode": "https://cdn.skytrip.com/boardingpass/qr/xxx.png",
    "barcode": "M1SAN/ZHANG   E1234567890"
  }
}
```

---

### 2.7 航班动态模块

#### 2.7.1 查询航班动态

**GET** `/flight-status`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| flightNo | string | 否 | 航班号 |
| date | string | 是 | 日期 |
| departure | string | 否 | 出发城市/机场 |
| arrival | string | 否 | 到达城市/机场 |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "flightNo": "CA1234",
    "date": "2026-06-10",
    "status": "DELAYED",
    "statusText": "延误",
    "departure": {
      "airport": "PEK",
      "airportName": "北京首都国际机场",
      "terminal": "T3",
      "gate": "A12",
      "scheduled": "2026-06-10T07:30:00+08:00",
      "estimated": "2026-06-10T07:45:00+08:00",
      "actual": null
    },
    "arrival": {
      "airport": "SHA",
      "airportName": "上海虹桥国际机场",
      "terminal": "T2",
      "carousel": "3",
      "scheduled": "2026-06-10T09:45:00+08:00",
      "estimated": "2026-06-10T10:00:00+08:00",
      "actual": null
    },
    "aircraft": "B738",
    "delay": {
      "minutes": 15,
      "reason": "流量控制"
    },
    "timeline": [
      {
        "time": "2026-06-10T06:30:00+08:00",
        "event": "值机开始"
      },
      {
        "time": "2026-06-10T07:00:00+08:00",
        "event": "开始登机"
      },
      {
        "time": "2026-06-10T07:45:00+08:00",
        "event": "预计起飞"
      }
    ]
  }
}
```

#### 2.7.2 订阅航班动态

**POST** `/flight-status/subscribe`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| flightNo | string | 是 | 航班号 |
| date | string | 是 | 日期 |
| channels | array | 是 | 推送渠道 SMS/EMAIL/PUSH/WECHAT |
| types | array | 是 | 订阅类型 CHECKIN/GATE_CHANGE/DELAY/CANCEL/BOARDING/BAGGAGE |

#### 2.7.3 取消订阅

**DELETE** `/flight-status/subscribe/{subscriptionId}`

---

### 2.8 会员中心模块

#### 2.8.1 获取个人信息

**GET** `/member/profile`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "userId": "U20260605001",
    "name": "张三",
    "gender": "MALE",
    "birthday": "1990-01-01",
    "phone": "138****8000",
    "email": "zhangsan@example.com",
    "avatar": "https://cdn.skytrip.com/avatar/xxx.jpg",
    "memberLevel": "GOLD",
    "miles": 15680,
    "memberNo": "FF12345678",
    "joinDate": "2024-01-15"
  }
}
```

#### 2.8.2 更新个人信息

**PUT** `/member/profile`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 否 | 姓名 |
| gender | string | 否 | MALE/FEMALE |
| birthday | string | 否 | 出生日期 |
| email | string | 否 | 邮箱 |
| avatar | string | 否 | 头像URL |

#### 2.8.3 证件管理

**GET** `/member/documents` - 获取证件列表

**POST** `/member/documents` - 添加证件

**PUT** `/member/documents/{docId}` - 更新证件

**DELETE** `/member/documents/{docId}` - 删除证件

#### 2.8.4 常用旅客管理

**GET** `/member/travelers` - 获取常用旅客列表

**POST** `/member/travelers` - 添加常用旅客

**PUT** `/member/travelers/{travelerId}` - 更新常用旅客

**DELETE** `/member/travelers/{travelerId}` - 删除常用旅客

#### 2.8.5 里程查询

**GET** `/member/miles`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "balance": 15680,
    "expiringMiles": 2000,
    "expiringDate": "2026-12-31",
    "levelProgress": {
      "current": "GOLD",
      "next": "PLATINUM",
      "required": 25000,
      "progress": 62.7
    },
    "records": [
      {
        "date": "2026-06-01",
        "type": "EARN",
        "miles": 580,
        "description": "CA1234 北京-上海"
      }
    ]
  }
}
```

#### 2.8.6 里程兑换

**POST** `/member/miles/redeem`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | TICKET/SERVICE |
| targetId | string | 是 | 兑换目标ID |
| miles | int | 是 | 使用里程数 |

---

### 2.9 帮助中心模块

#### 2.9.1 获取FAQ列表

**GET** `/help/faq`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| category | string | 否 | BOOKING/CHECKIN/REFUND/BAGGAGE/OTHER |
| keyword | string | 否 | 搜索关键词 |

#### 2.9.2 获取退改政策

**GET** `/help/refund-policy`

#### 2.9.3 获取行李规定

**GET** `/help/baggage-rules`

#### 2.9.4 提交投诉建议

**POST** `/help/feedback`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | COMPLAINT/SUGGESTION |
| orderId | string | 否 | 关联订单 |
| content | string | 是 | 内容 |
| contactPhone | string | 否 | 联系电话 |
| attachments | array | 否 | 附件URL列表 |

---

### 2.10 文件上传模块

#### 2.10.1 上传单个文件

**POST** `/file/upload`

**请求格式：** `multipart/form-data`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 文件（图片或视频） |

**支持的文件类型：**

| 类型 | 格式 |
|------|------|
| 图片 | jpg, jpeg, png, gif, webp, bmp |
| 视频 | mp4, avi, mov, wmv, flv, mkv |

**文件限制：**
- 单文件最大：50MB
- 请求最大：100MB

**响应示例：**
```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "url": "http://localhost:8080/api/images/2026/06/18/abc123def456.jpg",
    "path": "/images/2026/06/18/abc123def456.jpg",
    "filename": "photo.jpg",
    "newFilename": "abc123def456.jpg",
    "fileType": "image",
    "extension": "jpg",
    "size": 102400
  }
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| url | string | 完整访问URL（IP + 端口 + 路径） |
| path | string | 后半段路径（存储时使用） |
| filename | string | 原始文件名 |
| newFilename | string | 新文件名（UUID） |
| fileType | string | 文件类型：image/video |
| extension | string | 文件扩展名 |
| size | long | 文件大小（字节） |

**错误响应：**

| 错误码 | 说明 |
|--------|------|
| 400 | 文件为空 |
| 400 | 文件大小超过限制 |
| 400 | 文件名为空 |
| 400 | 不支持的文件类型 |
| 500 | 文件上传失败 |

---

#### 2.10.2 批量上传文件

**POST** `/file/upload/batch`

**请求格式：** `multipart/form-data`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| files | File[] | 是 | 文件数组（最多10个） |

**响应示例：**
```json
{
  "code": 200,
  "message": "上传成功",
  "data": [
    {
      "url": "http://localhost:8080/api/images/2026/06/18/abc123.jpg",
      "path": "/images/2026/06/18/abc123.jpg",
      "filename": "photo1.jpg",
      "newFilename": "abc123.jpg",
      "fileType": "image",
      "extension": "jpg",
      "size": 102400
    },
    {
      "url": "http://localhost:8080/api/images/2026/06/18/def456.mp4",
      "path": "/images/2026/06/18/def456.mp4",
      "filename": "video1.mp4",
      "newFilename": "def456.mp4",
      "fileType": "video",
      "extension": "mp4",
      "size": 5242880
    }
  ]
}
```

**错误响应：**

| 错误码 | 说明 |
|--------|------|
| 400 | 请选择文件 |
| 400 | 单次最多上传10个文件 |

---

#### 2.10.3 文件访问

**GET** `/images/{path}`

> 无需认证，直接通过 URL 访问已上传的文件

**访问示例：**
```
http://localhost:8080/api/images/2026/06/18/abc123.jpg
```

**存储结构：**
```
D:\uploads\images\
└── 2026\
    └── 06\
        └── 18\
            ├── abc123.jpg
            ├── def456.mp4
            └── ...
```

**说明：**
- 文件按日期自动分目录存储（年/月/日）
- 文件名使用 UUID 生成，避免冲突
- 上传后返回完整 URL 和后半段路径
- 前端存储时建议使用 `path`（后半段），显示时拼接服务器地址

---

## 3. 管理端接口

### 3.1 认证与权限

#### 3.1.1 管理员登录

**POST** `/admin/auth/login`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |
| mfaCode | string | 否 | 多因素认证码 |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "userId": "A20260605001",
    "username": "admin",
    "name": "管理员",
    "roles": ["ADMIN"],
    "permissions": ["flight:read", "flight:write", "checkin:all"],
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 1800
  }
}
```

---

### 3.2 航班调度模块

#### 3.2.1 航班计划列表

**GET** `/admin/flights`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | string | 否 | 日期 |
| route | string | 否 | 航线 |
| status | string | 否 | 状态 |
| airline | string | 否 | 航司 |
| keyword | string | 否 | 关键词 |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "flightId": "FL20260610001",
        "flightNo": "CA1234",
        "date": "2026-06-10",
        "route": {
          "departure": "PEK",
          "arrival": "SHA",
          "departureName": "北京首都",
          "arrivalName": "上海虹桥"
        },
        "schedule": {
          "departureTime": "07:30",
          "arrivalTime": "09:45",
          "duration": 135
        },
        "aircraft": {
          "type": "B738",
          "registration": "B-1234"
        },
        "status": "SCHEDULED",
        "passengers": {
          "total": 156,
          "checkedIn": 128
        }
      }
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "total": 186
    }
  }
}
```

**新增Flight字段：** `iataDelayCode`（IATA延误代码）、`compensationRule`（补偿规则）、`actualDepartureTime`（实际出发时间）、`actualArrivalTime`（实际到达时间）

---

#### 3.2.2 创建航班

**POST** `/admin/flights`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| flightNo | string | 是 | 航班号 |
| date | string | 是 | 航班日期 |
| route | object | 是 | 航线信息 |
| schedule | object | Is | 时刻信息 |
| aircraft | object | 是 | 机型信息 |
| flightType | string | 是 | DOMESTIC/INTERNATIONAL/REGIONAL |
| stops | array | 否 | 经停信息 |
| remark | string | 否 | 备注 |

**route：**
```json
{
  "departure": "PEK",
  "arrival": "SHA"
}
```

**schedule：**
```json
{
  "departureTime": "07:30",
  "arrivalTime": "09:45"
}
```

**aircraft：**
```json
{
  "type": "B738",
  "registration": "B-1234"
}
```

#### 3.2.3 编辑航班

**PUT** `/admin/flights/{flightId}`

#### 3.2.4 删除航班

**DELETE** `/admin/flights/{flightId}`

#### 3.2.5 批量操作

**POST** `/admin/flights/batch`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| flightIds | array | 是 | 航班ID列表 |
| action | string | 是 | CANCEL/UPDATE_STATUS |
| params | object | 否 | 操作参数 |

#### 3.2.6 航班时刻表

**GET** `/admin/flights/schedule`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| view | string | 是 | CALENDAR/TIMELINE/ROUTE |
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |
| route | string | 否 | 航线筛选 |

#### 3.2.7 不正常航班处理

**POST** `/admin/flights/{flightId}/irregular`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | DELAY/CANCEL/DIVERSION/RETURN |
| reason | string | 是 | 原因 |
| newSchedule | object | 否 | 新时刻（延误时） |
| notifyPassengers | boolean | 是 | 是否通知旅客 |
| arrangements | object | 否 | 保障安排 |

**arrangements：**
```json
{
  "meal": true,
  "accommodation": false,
  "transportation": true
}
```

#### 3.2.8 航班日志

**GET** `/admin/flights/{flightId}/logs`

---

### 3.3 不正常航班(IROPS)
| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 不正常航班记录列表 | GET | /admin/irop/operations | 分页查询，支持flightNo/type/status筛选 |
| 受影响旅客列表 | GET | /admin/irop/passengers/{flightId} | 查询某航班所有受影响旅客 |
| 自动改签结果列表 | GET | /admin/irop/rebookings | 分页查询，支持flightId/status筛选 |
| 确认/拒绝自动改签 | POST | /admin/irop/rebookings/{rebookingId}/confirm | body: {action: "APPROVED"\|"REJECTED"} |
| 手动改签 | POST | /admin/irop/manual-rebook | body: {orderId, newFlightId, newCabinClass} |
| 补偿规则查询 | GET | /admin/irop/compensation-rules | 返回补偿规则JSON |
| IATA延误原因代码 | GET | /admin/irop/delay-codes | 返回14个IATA标准延误代码 |

---

### 3.3 旅客服务模块

#### 3.3.1 值机管理

**GET** `/admin/checkin/{flightId}` - 获取航班值机信息

**POST** `/admin/checkin/{flightId}/open` - 开放值机

**POST** `/admin/checkin/{flightId}/close` - 关闭值机

**POST** `/admin/checkin/{flightId}/checkin` - 手动值机

**请求参数：**
```json
{
  "passengerIndex": 0,
  "seatRow": 12,
  "seatColumn": "A"
}
```

**POST** `/admin/checkin/{flightId}/cancel-checkin` - 取消值机

**POST** `/admin/checkin/{flightId}/auto-assign` - 自动分配座位

**GET** `/admin/checkin/{flightId}/export` - 导出值机名单

#### 3.3.2 登机口管理

**GET** `/admin/gates` - 获取登机口列表

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| terminal | string | 否 | 航站楼 |
| date | string | 是 | 日期 |

**POST** `/admin/gates/assign` - 分配登机口

**请求参数：**
```json
{
  "flightId": "FL20260610001",
  "gate": "A12",
  "startTime": "2026-06-10T06:30:00+08:00",
  "endTime": "2026-06-10T08:00:00+08:00"
}
```

**PUT** `/admin/gates/change` - 变更登机口

**GET** `/admin/gates/board` - 登机状态

#### 3.3.3 座位管理

**GET** `/admin/seats/{flightId}` - 获取航班座位图

**POST** `/admin/seats/{flightId}/lock` - 锁定座位

**POST** `/admin/seats/{flightId}/unlock` - 解锁座位

#### 3.3.4 旅客查询

**GET** `/admin/passengers`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 否 | 姓名 |
| idNumber | string | 否 | 证件号 |
| flightNo | string | 否 | 航班号 |
| dateRange | array | 否 | 日期范围 |
| pnr | string | 否 | 订座编号 |
| phone | string | 否 | 手机号 |

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "passengerId": "P20260605001",
        "name": "张三",
        "gender": "MALE",
        "idType": "ID_CARD",
        "idNumber": "110***********1234",
        "phone": "138****8000",
        "memberLevel": "GOLD",
        "specialMark": [],
        "blacklist": false,
        "recentTrips": [
          {
            "flightNo": "CA1234",
            "date": "2026-06-01",
            "route": "PEK-SHA"
          }
        ]
      }
    ],
    "pagination": {
      "page": 1,
      "pageSize": 20,
      "total": 1
    }
  }
}
```

---

### 3.x 特殊旅客服务(SSR)
| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 可用SSR代码列表 | GET | /admin/ssr/codes | 支持category筛选 |
| SSR请求列表 | GET | /admin/ssr/requests | 分页，支持flightId/status筛选 |
| 提交SSR请求 | POST | /admin/ssr/submit | body: {orderId, orderPassengerId, ssrCode, remark} |
| 处理SSR请求 | POST | /admin/ssr/requests/{requestId}/process | body: {action: "APPROVED"\|"REJECTED"} |
| 订单SSR查询 | GET | /admin/ssr/orders/{orderId} | 查某订单的SSR申请 |

---

### 3.4 机组管理模块

#### 3.4.1 机组排班

**GET** `/admin/crew/schedule`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |
| department | string | 否 | 部门 |
| qualification | string | 否 | 机型资质 |

**POST** `/admin/crew/schedule` - 创建排班

**请求参数：**
```json
{
  "crewId": "C20260605001",
  "flightId": "FL20260610001",
  "role": "CAPTAIN",
  "date": "2026-06-10"
}
```

**POST** `/admin/crew/schedule/auto` - 自动排班

**请求参数：**
```json
{
  "startDate": "2026-06-10",
  "endDate": "2026-06-16",
  "flights": ["FL20260610001", "FL20260610002"]
}
```

**DELETE** `/admin/crew/schedule/{scheduleId}` - 删除排班

#### 3.4.2 资质管理

**GET** `/admin/crew/{crewId}/qualifications`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "crewId": "C20260605001",
    "name": "张机长",
    "qualifications": [
      {
        "type": "LICENSE",
        "name": "飞行执照",
        "number": "PL123456",
        "issueDate": "2020-01-15",
        "expireDate": "2028-01-15",
        "status": "VALID"
      },
      {
        "type": "AIRCRAFT",
        "name": "B738机长资质",
        "level": "CAPTAIN",
        "issueDate": "2022-06-01",
        "expireDate": "2026-06-12",
        "status": "EXPIRING_SOON"
      },
      {
        "type": "MEDICAL",
        "name": "年度体检",
        "issueDate": "2025-12-15",
        "expireDate": "2026-12-15",
        "status": "VALID"
      }
    ]
  }
}
```

**POST** `/admin/crew/{crewId}/qualifications` - 添加资质

**PUT** `/admin/crew/{crewId}/qualifications/{qualId}` - 更新资质

#### 3.4.3 机组动态

**GET** `/admin/crew/dynamics`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 否 | FLYING/STANDBY/REST/TRAINING |
| crewId | string | 否 | 机组ID |

---

### 3.x 机组合规
| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 合规看板 | GET | /admin/crew-compliance/dashboard | 返回预警统计 |
| 机组飞行时间 | GET | /admin/crew-compliance/summary/{crewId} | param: days(默认30) |
| 合规预警列表 | GET | /admin/crew-compliance/warnings | 返回所有预警 |

---

### 3.5 运营监控模块

#### 3.5.1 监控大屏数据

**GET** `/admin/monitor/dashboard`

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "summary": {
      "planned": 186,
      "executed": 182,
      "executionRate": 97.8,
      "normal": 168,
      "normalRate": 92.3,
      "delayed": 12,
      "cancelled": 2,
      "passengers": 28560
    },
    "statusDistribution": {
      "SCHEDULED": 42,
      "CHECKING_IN": 38,
      "BOARDING": 35,
      "FLYING": 45,
      "ARRIVED": 22,
      "DELAYED": 12,
      "CANCELLED": 2
    },
    "delayAnalysis": {
      "weather": 5,
      "flowControl": 3,
      "mechanical": 2,
      "other": 2
    },
    "realtimeFlights": [
      {
        "flightNo": "CA1234",
        "status": "FLYING",
        "position": {
          "lat": 35.123,
          "lng": 118.456
        }
      }
    ],
    "recentEvents": [
      {
        "time": "2026-06-10T14:28:00+08:00",
        "event": "CA1234 已起飞"
      },
      {
        "time": "2026-06-10T14:25:00+08:00",
        "event": "MU5678 延误至 15:30"
      }
    ]
  }
}
```

**WebSocket 连接：** `wss://api.skytrip.com/v1/admin/monitor/ws`

#### 3.5.2 告警中心

**GET** `/admin/alerts`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| level | string | 否 | URGENT/IMPORTANT/NORMAL/INFO |
| type | string | 否 | 类型筛选 |
| status | string | 否 | PENDING/RESOLVED |

**POST** `/admin/alerts/{alertId}/resolve` - 处理告警

#### 3.5.3 统计概览

**GET** `/admin/statistics`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| dimension | string | 是 | FLIGHT/PASSENGER/REVENUE/SERVICE |
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |
| groupBy | string | 否 | DAY/WEEK/MONTH |

---

### 3.x 地面保障
| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 今日保障概览 | GET | /admin/ground/today | 返回准点率统计 |
| 航班保障节点 | GET | /admin/ground/nodes/{flightId} | 返回15个标准节点 |
| 保障记录列表 | GET | /admin/ground | 分页查询 |
| 更新节点时间 | PUT | /admin/ground/nodes/{nodeId}/time | body: {field: "actualTime", value: "ISO datetime"} |

---

### 3.6 客票管理模块

#### 3.6.1 订座管理

**GET** `/admin/tickets/bookings`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pnr | string | 否 | PNR编号 |
| passengerName | string | 否 | 旅客姓名 |
| flightNo | string | 否 | 航班号 |

**GET** `/admin/tickets/bookings/{pnr}` - 订座详情

#### 3.6.2 票价管理

**GET** `/admin/tickets/fares` - 运价列表

**POST** `/admin/tickets/fares` - 创建运价

**PUT** `/admin/tickets/fares/{fareId}` - 更新运价

#### 3.6.3 退改管理

**GET** `/admin/tickets/refunds` - 退票列表

**POST** `/admin/tickets/refunds/{refundId}/approve` - 审核退票

**GET** `/admin/tickets/changes` - 改签列表

**POST** `/admin/tickets/changes/{changeId}/approve` - 审核改签

---

### 3.7 报表中心模块

#### 3.7.1 运营报表

**GET** `/admin/reports/operation`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | DAILY/DELAY/PASSENGER/CREW/AIRPORT |
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |
| format | string | 否 | JSON/EXCEL/PDF |

#### 3.7.2 收入报表

**GET** `/admin/reports/revenue`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | DAILY/ROUTE/CABIN/AUXILIARY |
| startDate | string | 是 | 开始日期 |
| endDate | string | 是 | 结束日期 |

#### 3.7.3 自定义报表

**GET** `/admin/reports/custom` - 自定义报表列表

**POST** `/admin/reports/custom` - 创建自定义报表

**POST** `/admin/reports/custom/{reportId}/generate` - 生成报表

---

### 3.8 系统管理模块

#### 3.8.1 用户管理

**GET** `/admin/system/users` - 用户列表

**POST** `/admin/system/users` - 创建用户

**请求参数：**
```json
{
  "username": "dispatcher01",
  "name": "调度员01",
  "phone": "13900139000",
  "email": "dispatch@airline.com",
  "roles": ["DISPATCHER"],
  "password": "InitPassword123"
}
```

**PUT** `/admin/system/users/{userId}` - 更新用户

**POST** `/admin/system/users/{userId}/disable` - 禁用用户

**POST** `/admin/system/users/{userId}/enable` - 启用用户

**POST** `/admin/system/users/{userId}/reset-password` - 重置密码

#### 3.8.2 角色权限

**GET** `/admin/system/roles` - 角色列表

**POST** `/admin/system/roles` - 创建角色

**请求参数：**
```json
{
  "name": "航班调度员",
  "code": "DISPATCHER",
  "permissions": [
    "flight:read",
    "flight:write",
    "flight:cancel",
    "monitor:read"
  ],
  "description": "航班调度相关权限"
}
```

**PUT** `/admin/system/roles/{roleId}` - 更新角色

**DELETE** `/admin/system/roles/{roleId}` - 删除角色

#### 3.8.3 基础数据管理

**GET/POST/PUT/DELETE** `/admin/system/master-data/airports` - 机场管理

**GET/POST/PUT/DELETE** `/admin/system/master-data/routes` - 航线管理

**GET/POST/PUT/DELETE** `/admin/system/master-data/aircraft-types` - 机型管理

**GET/POST/PUT/DELETE** `/admin/system/master-data/fleet` - 机队管理

**GET/POST/PUT/DELETE** `/admin/system/master-data/departments` - 部门管理

#### 3.8.4 操作日志

**GET** `/admin/system/logs`

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| operator | string | 否 | 操作人 |
| module | string | 否 | 操作模块 |
| action | string | 否 | 操作类型 |
| startDate | string | 否 | 开始时间 |
| endDate | string | 否 | 结束时间 |

#### 3.8.5 系统配置

**GET** `/admin/system/config` - 获取配置

**PUT** `/admin/system/config` - 更新配置

**请求参数：**
```json
{
  "checkinOpenHours": 24,
  "boardingCloseMinutes": 15,
  "defaultRefundRule": {
    "before2h": 0.1,
    "before4h": 0.05,
    "after4h": 0
  },
  "notificationTemplates": {
    "SMS_DELAY": "尊敬的旅客，您乘坐的{flightNo}航班延误至{newTime}...",
    "EMAIL_ETICKET": "您的电子行程单已生成..."
  }
}
```

---

## 4. WebSocket 接口

### 4.1 航班动态推送

**连接地址：** `wss://api.skytrip.com/v1/ws/flight-status`

**认证：** URL参数携带token

**订阅消息：**
```json
{
  "action": "SUBSCRIBE",
  "flights": ["CA1234", "MU5678"]
}
```

**推送消息：**
```json
{
  "type": "FLIGHT_UPDATE",
  "data": {
    "flightNo": "CA1234",
    "status": "DELAYED",
    "departure": {
      "estimated": "2026-06-10T07:45:00+08:00"
    },
    "delay": {
      "minutes": 15,
      "reason": "流量控制"
    }
  },
  "timestamp": "2026-06-10T07:15:00+08:00"
}
```

### 4.2 管理端监控推送

**连接地址：** `wss://api.skytrip.com/v1/admin/ws/monitor`

**推送消息类型：**
- `FLIGHT_STATUS` - 航班状态变更
- `ALERT` - 新告警
- `DASHBOARD_UPDATE` - 大屏数据更新
- `CHECKIN_UPDATE` - 值机数据更新

### 4.3 新增WebSocket端点

| 端点 | 说明 | 推送消息类型 |
|------|------|-------------|
| /ws/passenger/notification?token=&userId= | 旅客端通知推送 | NOTIFICATION / REBOOKING |
| /admin/ws/irregular?token= | 管理端IROPS推送 | IRREGULAR_UPDATE / ADMIN_NOTIFICATION / GROUND_UPDATE / CREW_COMPLIANCE_ALERT |

---

## 5. 附录

### 5.1 状态枚举

#### 订单状态

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

#### 航班状态

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

#### 机组状态

| 状态 | 说明 |
|------|------|
| FLYING | 飞行中 |
| STANDBY | 待命 |
| REST | 休息 |
| TRAINING | 培训 |
| LEAVE | 休假 |

### 5.2 错误响应示例

```json
{
  "code": 400,
  "message": "请求参数错误",
  "errors": [
    {
      "field": "phone",
      "message": "手机号格式不正确"
    }
  ],
  "timestamp": "2026-06-05T10:30:00Z",
  "requestId": "req_abc123def456"
}
```

### 5.3 接口限流

| 接口类型 | 限流策略 |
|---------|---------|
| 搜索接口 | 100次/分钟/用户 |
| 下单接口 | 10次/分钟/用户 |
| 支付接口 | 5次/分钟/用户 |
| 短信接口 | 1次/分钟/手机号 |
| 管理端接口 | 1000次/分钟/用户 |

---

*文档结束*
