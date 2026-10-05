import request from '@/utils/request'

/** 运营报表 */
export function getOperationReport(params: {
  type: string
  startDate: string
  endDate: string
  format?: string
}) {
  return request.get('/admin/reports/operation', { params })
}

/** 收入报表 */
export function getRevenueReport(params: {
  type: string
  startDate: string
  endDate: string
}) {
  return request.get('/admin/reports/revenue', { params })
}

/** 自定义报表列表 */
export function getCustomReportList(params?: Record<string, any>) {
  return request.get('/admin/reports/custom', { params })
}

/** 创建自定义报表 */
export function createCustomReport(data: Record<string, any>) {
  return request.post('/admin/reports/custom', data)
}

/** 生成报表 */
export function generateReport(reportId: string) {
  return request.post(`/admin/reports/custom/${reportId}/generate`)
}
