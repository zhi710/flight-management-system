// ========== 航班状态 ==========
export const FLIGHT_STATUS_MAP: Record<string, { label: string; type: string }> = {
  SCHEDULED:    { label: '已计划',  type: 'info' },
  BOARDING:     { label: '登机中',  type: 'warning' },
  DEPARTED:     { label: '已起飞',  type: 'success' },
  FLYING:       { label: '飞行中',  type: 'success' },
  ARRIVED:      { label: '已到达',  type: 'success' },
  COMPLETED:    { label: '已完成',  type: '' },
  DELAYED:      { label: '延误',    type: 'danger' },
  CANCELLED:    { label: '已取消',  type: 'info' },
  DIVERTED:     { label: '备降',    type: 'warning' },
  RETURNED:     { label: '返航',    type: 'warning' },
}

// ========== 订单状态 ==========
export const ORDER_STATUS_MAP: Record<string, { label: string; type: string }> = {
  PENDING_PAYMENT: { label: '待支付',  type: 'warning' },
  PAID:            { label: '已支付',  type: 'success' },
  ISSUED:          { label: '已出票',  type: 'success' },
  CHECKED_IN:      { label: '已值机',  type: '' },
  BOARDING:        { label: '登机中',  type: 'warning' },
  DEPARTED:        { label: '已起飞',  type: 'success' },
  ARRIVED:         { label: '已到达',  type: 'success' },
  COMPLETED:       { label: '已完成',  type: 'info' },
  CANCELLED:       { label: '已取消',  type: 'info' },
  REFUNDING:       { label: '退票中',  type: 'warning' },
  REFUNDED:        { label: '已退票',  type: 'info' },
}

// ========== 机组状态 ==========
export const CREW_STATUS_MAP: Record<string, { label: string; type: string }> = {
  FLYING:    { label: '飞行中',  type: 'success' },
  STANDBY:   { label: '待命',    type: 'warning' },
  REST:      { label: '休息',    type: 'info' },
  TRAINING:  { label: '培训',    type: '' },
  LEAVE:     { label: '休假',    type: 'info' },
  GROUNDED:  { label: '停飞',    type: 'danger' },
}

// ========== 舱位等级 ==========
export const CABIN_CLASS_MAP: Record<string, string> = {
  ECONOMY:  '经济舱',
  BUSINESS: '公务舱',
  FIRST:    '头等舱',
}

// ========== 航班类型 ==========
export const FLIGHT_TYPE_MAP: Record<string, string> = {
  DOMESTIC:      '国内',
  INTERNATIONAL: '国际',
  REGIONAL:      '地区',
}

// ========== 告警级别 ==========
export const ALERT_LEVEL_MAP: Record<string, { label: string; type: string }> = {
  URGENT:    { label: '紧急',  type: 'danger' },
  IMPORTANT: { label: '重要',  type: 'warning' },
  NORMAL:    { label: '普通',  type: '' },
  INFO:      { label: '信息',  type: 'info' },
}

// ========== 证件类型 ==========
export const ID_TYPE_MAP: Record<string, string> = {
  ID_CARD:           '身份证',
  PASSPORT:          '护照',
  HM_PASSPORT:       '港澳通行证',
  TAIWAN_PASSPORT:   '台湾通行证',
  OTHER:             '其他',
}

// ========== 旅客类型 ==========
export const PASSENGER_TYPE_MAP: Record<string, string> = {
  ADULT:   '成人',
  CHILD:   '儿童',
  INFANT:  '婴儿',
}

// ========== 性别 ==========
export const GENDER_MAP: Record<string, string> = {
  MALE:   '男',
  FEMALE: '女',
}

// ========== 支付方式 ==========
export const PAY_METHOD_MAP: Record<string, string> = {
  WECHAT:     '微信支付',
  ALIPAY:     '支付宝',
  BANK_CARD:  '银行卡',
  CREDIT:     '信用卡',
}

// ========== 会员等级 ==========
// 底色 + 字色都取自设计令牌（--tier-*-bg / -fg），不再是写死的十六进制。
// 走「浅底深字」而不是原来的「深底白字」：银卡是浅灰，配白字只有 1.75:1，
// 原来五个等级的白字对比度（3.08/1.75/2.19/3.24/4.15）全部不合格。
// 色值定义在 design-system/tokens.css 的「业务扩展色板」一节。
export const MEMBER_LEVEL_MAP: Record<string, { label: string; bg: string; fg: string }> = {
  NORMAL:   { label: '普通会员', bg: 'var(--tier-normal-bg)',   fg: 'var(--tier-normal-fg)' },
  SILVER:   { label: '银卡会员', bg: 'var(--tier-silver-bg)',   fg: 'var(--tier-silver-fg)' },
  GOLD:     { label: '金卡会员', bg: 'var(--tier-gold-bg)',     fg: 'var(--tier-gold-fg)' },
  PLATINUM: { label: '铂金会员', bg: 'var(--tier-platinum-bg)', fg: 'var(--tier-platinum-fg)' },
  DIAMOND:  { label: '钻石会员', bg: 'var(--tier-diamond-bg)',  fg: 'var(--tier-diamond-fg)' },
}
