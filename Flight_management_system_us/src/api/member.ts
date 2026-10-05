import request from '@/utils/request'

// GET /member/profile — doc section 2.8.1
export function getProfile() {
  return request.get('/member/profile')
}

// PUT /member/profile — doc section 2.8.2
export function updateProfile(data: {
  name?: string
  gender?: string
  birthday?: string
  email?: string
  avatar?: string
}) {
  return request.put('/member/profile', data)
}

// POST /file/upload — 上传文件
export function uploadFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// POST /member/realname — 提交实名认证
export function submitRealname(data: {
  name: string
  idType: string
  idNumber: string
}) {
  return request.post('/member/realname', data)
}

// GET /member/documents — doc section 2.8.3
export function getDocuments() {
  return request.get('/member/documents')
}

// POST /member/documents
export function addDocument(data: any) {
  return request.post('/member/documents', data)
}

// PUT /member/documents/{docId} — doc section 2.8.3
export function updateDocument(docId: string, data: any) {
  return request.put(`/member/documents/${docId}`, data)
}

// DELETE /member/documents/{docId}
export function deleteDocument(docId: string) {
  return request.delete(`/member/documents/${docId}`)
}

// GET /member/travelers — doc section 2.8.4
export function getTravelers() {
  return request.get('/member/travelers')
}

// POST /member/travelers
export function addTraveler(data: any) {
  return request.post('/member/travelers', data)
}

// PUT /member/travelers/{travelerId} — doc section 2.8.4
export function updateTraveler(travelerId: string, data: any) {
  return request.put(`/member/travelers/${travelerId}`, data)
}

// DELETE /member/travelers/{travelerId}
export function deleteTraveler(travelerId: string) {
  return request.delete(`/member/travelers/${travelerId}`)
}

// GET /member/miles — doc section 2.8.5
export function getMiles() {
  return request.get('/member/miles')
}

// POST /member/miles/redeem — 后端使用 @RequestBody MilesRedeemDTO 接收
export function redeemMiles(data: {
  type: string
  targetId: string
  miles: number
}) {
  return request.post('/member/miles/redeem', data)
}

// POST /member/change-password — 修改密码
export function changePassword(data: {
  oldPassword: string
  newPassword: string
}) {
  return request.post('/member/change-password', data)
}
