type MessageHandler = (data: any) => void

const WS_BASE = import.meta.env.DEV
  ? 'ws://localhost:8080/api/admin/ws'
  : `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/v1/admin/ws`

function getToken(): string | null {
  return localStorage.getItem('skyops_token')
}

/**
 * 管理端 IROPS WebSocket（接收不正常航班推送、告警、地服、机组合规）
 */
class AdminIrregularSocket {
  private ws: WebSocket | null = null
  private handlers = new Map<string, MessageHandler[]>()
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null

  connect() {
    if (this.ws?.readyState === WebSocket.OPEN) return
    const token = getToken()
    if (!token) return

    this.ws = new WebSocket(`${WS_BASE}/irregular?token=${token}`)

    this.ws.onopen = () => {
      // 连接成功
    }

    this.ws.onmessage = (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data)
        const hList = this.handlers.get(msg.type) || []
        hList.forEach(fn => fn(msg.data || msg))
        const wildcard = this.handlers.get('*') || []
        wildcard.forEach(fn => fn(msg))
      } catch { /* ignore parse errors */ }
    }

    this.ws.onclose = () => {
      this.ws = null
      this.reconnectTimer = setTimeout(() => this.connect(), 5000)
    }

    this.ws.onerror = () => {
      this.ws?.close()
    }
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    this.ws?.close()
    this.ws = null
  }

  /**
   * 监听指定类型的消息
   * @param type 消息类型 IRREGULAR_UPDATE / ADMIN_NOTIFICATION / REBOOKING_RESULT / GATE_CHANGE / CREW_COMPLIANCE_ALERT
   * @param handler 回调函数
   */
  on(type: string, handler: MessageHandler) {
    const list = this.handlers.get(type) || []
    list.push(handler)
    this.handlers.set(type, list)
  }

  off(type: string, handler: MessageHandler) {
    const list = this.handlers.get(type) || []
    const idx = list.indexOf(handler)
    if (idx > -1) list.splice(idx, 1)
  }
}

export const adminIrregularSocket = new AdminIrregularSocket()
