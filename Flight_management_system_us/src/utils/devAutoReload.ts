/**
 * 开发模式：后端重启后自动刷新页面。
 *
 * 后端每次启动会生成一个实例标识（GET /api/system/instance），这里定时轮询它，
 * 一旦标识变化就说明后端已重新编译并启动，直接 reload 页面，省去手动 F5。
 *
 * 仅在 dev 环境生效：生产构建时 `import.meta.env.DEV` 为 false，函数直接返回，
 * 不会产生任何轮询请求。
 */
const POLL_INTERVAL = 3000

export function startDevAutoReload() {
  if (!import.meta.env.DEV) return

  // 首次拿到标识只作记录，之后标识变化才刷新，避免页面刚打开就白刷一次
  let currentInstanceId: string | null = null

  async function poll() {
    try {
      const res = await fetch('/api/system/instance', { cache: 'no-store' })
      // 后端正在启动/已停止时代理会返回 5xx，此时什么都不做，等下一次轮询
      if (!res.ok) return
      const body = await res.json()
      const instanceId: string | undefined = body?.data?.instanceId
      if (!instanceId) return

      if (currentInstanceId && instanceId !== currentInstanceId) {
        console.info('[dev] 检测到后端已重启，正在刷新页面…')
        window.location.reload()
        return
      }
      currentInstanceId = instanceId
    } catch {
      // 后端不可用（重启中）属于预期情况，静默忽略
    }
  }

  void poll()
  window.setInterval(poll, POLL_INTERVAL)
}
