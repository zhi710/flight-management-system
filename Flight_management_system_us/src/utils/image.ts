/**
 * 图片 URL 处理工具
 * <p>数据库中存储的是后半段路径（如 /images/2026/06/18/xxx.jpg），
 * 前端显示时需要拼接 /api 前缀，通过 Vite 代理转发到后端。</p>
 */

/**
 * 处理图片 URL
 * <p>拼接 /api 前缀，通过 Vite 代理转发到后端</p>
 *
 * @param path 图片路径（后半段或完整URL）
 * @returns 可直接用于 img src 的地址
 */
export function getImageUrl(path: string | undefined | null): string {
  if (!path) return ''

  // 已经是完整 URL，直接返回
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }

  // 相对路径，加上 /api 前缀（后端 context-path 是 /api）
  if (path.startsWith('/')) {
    return '/api' + path  // /api/images/2026/06/18/xxx.jpg
  }

  // 没有 / 开头，加上 /api/images/ 前缀
  return '/api/images/' + path
}

/**
 * 处理头像 URL
 * <p>如果头像为空，返回空字符串（由组件显示默认图标）</p>
 *
 * @param avatar 头像路径
 * @returns 完整的头像访问地址
 */
export function getAvatarUrl(avatar: string | undefined | null): string {
  return getImageUrl(avatar)
}

/**
 * 处理航司 Logo URL
 *
 * @param logo Logo 路径
 * @returns 完整的 Logo 访问地址
 */
export function getAirlineLogoUrl(logo: string | undefined | null): string {
  return getImageUrl(logo)
}
