import request from '@/utils/request'

/** 机组排班列表 */
export function getCrewSchedule(params: {
  startDate: string
  endDate: string
  department?: string
  qualification?: string
}) {
  return request.get('/admin/crew/schedule', { params })
}

/** 创建排班 */
export function createCrewSchedule(data: {
  crewId: string
  flightId: string
  role: string
  date: string
}) {
  return request.post('/admin/crew/schedule', data)
}

/** 自动排班 */
export function autoSchedule(data: {
  startDate: string
  endDate: string
  skipWeekends?: boolean
}) {
  return request.post('/admin/crew/schedule/auto', data)
}

/** 删除排班 */
export function deleteCrewSchedule(scheduleId: string) {
  return request.delete(`/admin/crew/schedule/${scheduleId}`)
}

/** 获取机组资质 */
export function getCrewQualifications(crewId: string) {
  return request.get(`/admin/crew/${crewId}/qualifications`)
}

/** 添加资质 */
export function addCrewQualification(crewId: string, data: Record<string, any>) {
  return request.post(`/admin/crew/${crewId}/qualifications`, data)
}

/** 更新资质 */
export function updateCrewQualification(crewId: string, qualId: string, data: Record<string, any>) {
  return request.put(`/admin/crew/${crewId}/qualifications/${qualId}`, data)
}

/** 删除资质 */
export function deleteCrewQualification(crewId: string, qualId: string) {
  return request.delete(`/admin/crew/${crewId}/qualifications/${qualId}`)
}

/** 机组名单（机组资质维护用） */
export function getCrewList(params?: { department?: string; keyword?: string }) {
  return request.get('/admin/crew/list', { params })
}

/** 机组动态 */
export function getCrewDynamics(params: { status?: string; crewId?: string }) {
  return request.get('/admin/crew/dynamics', { params })
}

/** 新增机组人员 */
export function createCrew(data: Record<string, any>) {
  return request.post('/admin/crew', data)
}

/** 修改机组人员 */
export function updateCrew(id: string, data: Record<string, any>) {
  return request.put(`/admin/crew/${id}`, data)
}

/** 删除机组人员 */
export function deleteCrew(id: string) {
  return request.delete(`/admin/crew/${id}`)
}
