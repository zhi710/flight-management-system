/**
 * 展示层格式化工具。
 * 只做"给屏幕看"的加工，不改数据本身；结果都是纯函数，便于在模板里直接调用。
 */

/**
 * 机场名简称：「北京首都国际机场」→「北京首都」。
 *
 * 列表页的列宽放不下 8 字全名，会折成两行把行高从 44px 撑到 86px；
 * 而运营在列表里只需要定位到城市，完整机场名在编辑弹窗与详情里已经展示。
 */
export function shortAirport(name?: string | null): string {
  if (!name) return ''
  return name.replace(/(国际|国内)?机场$/, '')
}

/**
 * 提取时刻，统一成 "HH:mm"。
 *
 * 要同时吃得下三种后端格式，否则会静默返回原串、把一整行撑成三行：
 *   "08:30"                  → 08:30
 *   "08:30:00"               → 08:30
 *   "2026-09-26T06:30"       → 06:30   ← 地服/航班接口是这种
 */
export function shortTime(value?: string | null): string {
  if (!value) return ''
  const timePart = value.includes('T') ? value.slice(value.indexOf('T') + 1) : value
  // 用解构默认值而不是 m[1]：tsconfig 开了 noUncheckedIndexedAccess，
  // 下标访问的结果是 string | undefined，直接调 .padStart 通不过类型检查。
  const [, h = '', mi = ''] = /^(\d{1,2}):(\d{2})/.exec(timePart) ?? []
  return h ? `${h.padStart(2, '0')}:${mi}` : value
}

/**
 * 时间统一成 "YYYY-MM-DD HH:mm"。
 *
 * 后端返回的是 ISO 串（"2026-09-25T00:36:17"），直接 prop 绑定到表格里
 * 中间那个 "T" 会原样露给运营看；秒对列表页也没有意义。
 * 顺带把列宽需求从 19 个字符压到 16 个（约省 20px）。
 */
export function fmtDateTime(value?: string | null): string {
  if (!value) return '—'
  const [, y = '', mo = '', d = '', h = '', mi = ''] =
    /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(value) ?? []
  return h ? `${y}-${mo}-${d} ${h}:${mi}` : value
}

/**
 * 只取 "MM-DD"，用于已经单独展示时刻、或年份不重要的场景（如当日保障列表）。
 * 与 shortTime 搭配使用：主行放时刻、副行放日期。
 */
export function fmtMonthDay(value?: string | null): string {
  if (!value) return ''
  const [, , mo = '', d = ''] = /^(\d{4})-(\d{2})-(\d{2})/.exec(value) ?? []
  return mo ? `${mo}-${d}` : value
}

/**
 * 长 ID 截短：雪花 ID（"U2103453799875624960"）有 19-20 个字符，
 * 在列表里既占地方又没人能一眼读完。保留头尾便于人工比对，
 * 完整值放 title 悬浮查看。
 */
export function shortId(value?: string | number | null, head = 6, tail = 6): string {
  const s = value === null || value === undefined ? '' : String(value)
  if (s.length <= head + tail + 1) return s
  return `${s.slice(0, head)}…${s.slice(-tail)}`
}

/**
 * 大数字加千分位，用于旅客数、金额。
 * 用 toLocaleString 而不是手写正则，避免出现 "1,2345" 这类错误。
 */
export function thousands(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : String(value)
}
