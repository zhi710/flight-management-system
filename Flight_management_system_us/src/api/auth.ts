import request from '@/utils/request'

// POST /auth/register — doc section 2.1.1
export function register(data: {
  phone: string
  smsCode: string
  password: string
  name?: string
}) {
  return request.post('/auth/register', data)
}

// POST /auth/login — doc section 2.1.2
export function login(data: {
  phone: string
  password: string
  captcha?: string
}) {
  return request.post('/auth/login', data)
}

// POST /auth/login/sms — 后端使用 @RequestBody SmsLoginDTO 接收
export function loginBySms(data: {
  phone: string
  smsCode: string
}) {
  return request.post('/auth/login/sms', data)
}

// POST /auth/sms/send — 后端使用 @RequestBody SmsSendDTO 接收
export function sendSmsCode(data: {
  phone: string
  type: 'register' | 'login' | 'resetPassword'
}) {
  return request.post('/auth/sms/send', data)
}

// GET /auth/oauth/{provider}
export function oauthLogin(provider: 'wechat' | 'alipay') {
  return request.get(`/auth/oauth/${provider}`)
}

// POST /auth/token/refresh — 后端使用 @RequestBody TokenRefreshDTO 接收
export function refreshToken(refreshToken: string) {
  return request.post('/auth/token/refresh', { refreshToken })
}
