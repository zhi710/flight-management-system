const TOKEN_KEY = 'skyops_token'
const REFRESH_TOKEN_KEY = 'skyops_refresh_token'
const USER_INFO_KEY = 'skyops_user_info'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function removeRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function getUserInfo(): Record<string, any> {
  const raw = localStorage.getItem(USER_INFO_KEY)
  return raw ? JSON.parse(raw) : {}
}

export function setUserInfo(info: Record<string, any>): void {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
}

export function removeUserInfo(): void {
  localStorage.removeItem(USER_INFO_KEY)
}

export function clearAuth(): void {
  removeToken()
  removeRefreshToken()
  removeUserInfo()
}
