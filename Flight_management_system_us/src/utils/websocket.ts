type MessageHandler = (data: any) => void

const WS_BASE = import.meta.env.DEV
  ? 'ws://localhost:8080/api/ws'
  : `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/v1/ws`

function getToken(): string | null {
  return localStorage.getItem('token')
}

function getUserId(): string | null {
  return localStorage.getItem('userId')
}

/**
 * 航班动态 WebSocket
 */
class FlightStatusSocket {
  private ws: WebSocket | null = null
  private handlers = new Map<string, MessageHandler[]>()
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private subscribed = new Set<string>()

  connect() {
    if (this.ws?.readyState === WebSocket.OPEN) return
    const token = getToken()
    if (!token) return

    this.ws = new WebSocket(`${WS_BASE}/flight-status?token=${token}`)

    this.ws.onopen = () => {
      this.subscribed.forEach(flightNo => {
        this.ws?.send(JSON.stringify({ action: 'SUBSCRIBE', flights: [flightNo] }))
      })
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (msg.type === 'FLIGHT_UPDATE' && msg.flightNo) {
          const hList = this.handlers.get(msg.flightNo) || []
          hList.forEach(fn => fn(msg))
          const wildcard = this.handlers.get('*') || []
          wildcard.forEach(fn => fn(msg))
        }
      } catch { }
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
    this.subscribed.clear()
    this.ws?.close()
    this.ws = null
  }

  subscribe(flightNo: string, handler: MessageHandler) {
    this.subscribed.add(flightNo)
    const list = this.handlers.get(flightNo) || []
    list.push(handler)
    this.handlers.set(flightNo, list)

    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ action: 'SUBSCRIBE', flights: [flightNo] }))
    }
    return () => this.unsubscribe(flightNo, handler)
  }

  private unsubscribe(flightNo: string, handler: MessageHandler) {
    const list = this.handlers.get(flightNo) || []
    const idx = list.indexOf(handler)
    if (idx > -1) list.splice(idx, 1)
    if (list.length === 0) {
      this.handlers.delete(flightNo)
      this.subscribed.delete(flightNo)
    }
  }
}

/**
 * 通知 WebSocket（旅客端接收通知短信/改签结果/航班动态）
 */
class NotificationSocket {
  private ws: WebSocket | null = null
  private handlers = new Map<string, MessageHandler[]>()
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private onNotificationCallback: ((data: any) => void) | null = null
  private onRebookingCallback: ((data: any) => void) | null = null
  private onSsrUpdateCallback: ((data: any) => void) | null = null

  connect() {
    if (this.ws?.readyState === WebSocket.OPEN) return
    const userId = getUserId()
    const token = getToken()
    if (!token || !userId) return

    this.ws = new WebSocket(`${WS_BASE}/passenger/notification?token=${token}&userId=${userId}`)

    this.ws.onopen = () => {
      // 连接成功
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (msg.type === 'NOTIFICATION' && this.onNotificationCallback) {
          this.onNotificationCallback(msg.data)
        } else if (msg.type === 'REBOOKING' && this.onRebookingCallback) {
          this.onRebookingCallback(msg.data)
        } else if (msg.type === 'SSR_UPDATE' && this.onSsrUpdateCallback) {
          this.onSsrUpdateCallback(msg.data)
        }
        // 通用路由
        const hList = this.handlers.get(msg.type) || []
        hList.forEach(fn => fn(msg.data))
        const wildcard = this.handlers.get('*') || []
        wildcard.forEach(fn => fn(msg))
      } catch { }
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
    this.onNotificationCallback = null
    this.onRebookingCallback = null
    this.ws?.close()
    this.ws = null
  }

  onNotification(callback: (data: any) => void) {
    this.onNotificationCallback = callback
  }

  onRebooking(callback: (data: any) => void) {
    this.onRebookingCallback = callback
  }

  onSsrUpdate(callback: (data: any) => void) {
    this.onSsrUpdateCallback = callback
  }

  on(type: string, handler: MessageHandler) {
    const list = this.handlers.get(type) || []
    list.push(handler)
    this.handlers.set(type, list)
  }
}

export const flightStatusSocket = new FlightStatusSocket()
export const notificationSocket = new NotificationSocket()
