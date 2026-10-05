import request from '@/utils/request'

/** 监控大屏数据 */
export function getDashboardData() {
  return request.get('/admin/monitor/dashboard')
}

/** 告警列表 */
export function getAlertList(params: {
  level?: string
  type?: string
  status?: string
  page?: number
  pageSize?: number
}) {
  return request.get('/admin/alerts', { params })
}

/** 告警统计 */
export function getAlertStats() {
  return request.get('/admin/alerts/stats')
}

/** 处理告警 */
export function resolveAlert(alertId: string, data?: Record<string, any>) {
  return request.post(`/admin/alerts/${alertId}/resolve`, data)
}

/** 统计概览 */
export function getStatistics(params: {
  dimension: string
  startDate: string
  endDate: string
  groupBy?: string
}) {
  return request.get('/admin/statistics', { params })
}
