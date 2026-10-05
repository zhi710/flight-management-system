import request from '@/utils/request'

export function getCrewComplianceDashboard() {
  return request.get('/admin/crew-compliance/dashboard')
}

export function getCrewSummary(crewId: string, days = 30) {
  return request.get(`/admin/crew-compliance/summary/${crewId}`, { params: { days } })
}

export function getComplianceWarnings() {
  return request.get('/admin/crew-compliance/warnings')
}
