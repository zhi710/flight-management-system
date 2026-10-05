import request from '@/utils/request'

// GET /carousel — 获取首页轮播图列表
export function getCarousels() {
  return request.get('/carousel')
}
