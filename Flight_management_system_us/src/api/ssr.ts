import request from '@/utils/request'

/** 获取可用的SSR代码列表 */
export function getSsrCodes(category?: string) {
  return request.get('/ssr/codes', { params: { category } })
}

/** 提交SSR申请 */
export function submitSsrRequest(data: {
  orderId: string
  orderPassengerId: string
  ssrCode: string
  remark?: string
}) {
  return request.post('/ssr/requests', data)
}

/** 查询订单的SSR申请 */
export function getSsrByOrder(orderId: string) {
  return request.get(`/ssr/orders/${orderId}`)
}
