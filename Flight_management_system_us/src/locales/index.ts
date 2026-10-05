import { createI18n } from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import enUs from 'element-plus/es/locale/lang/en'
import thTh from 'element-plus/es/locale/lang/th'
import viVn from 'element-plus/es/locale/lang/vi'
import idId from 'element-plus/es/locale/lang/id'
import msMy from 'element-plus/es/locale/lang/ms'

import zhCN from './zh-CN'
import enUS from './en-US'
import thTH from './th-TH'
import viVN from './vi-VN'
import idID from './id-ID'
import msMY from './ms-MY'

export interface LocaleOption {
  value: string
  /** 语言自称（母语写法），下拉里显示这个，用户才认得出 */
  label: string
  /** 切换器上显示的短标识 */
  icon: string
  /** 该语言覆盖的地区，用来说明"面向哪里" */
  region: string
}

/** 全部可用语言 */
export const localeOptions: LocaleOption[] = [
  { value: 'zh-CN', label: '简体中文', icon: '中', region: '中国大陆' },
  { value: 'en-US', label: 'English', icon: 'EN', region: 'International' },
  { value: 'th-TH', label: 'ภาษาไทย', icon: 'TH', region: 'ประเทศไทย' },
  { value: 'vi-VN', label: 'Tiếng Việt', icon: 'VI', region: 'Việt Nam' },
  { value: 'id-ID', label: 'Bahasa Indonesia', icon: 'ID', region: 'Indonesia' },
  { value: 'ms-MY', label: 'Bahasa Melayu', icon: 'MY', region: 'Malaysia' },
]

/** Element Plus 组件自身文案的语言包映射（日期选择器、分页、对话框按钮等） */
export const elementPlusLocales: Record<string, any> = {
  'zh-CN': zhCn,
  'en-US': enUs,
  'th-TH': thTh,
  'vi-VN': viVn,
  'id-ID': idId,
  'ms-MY': msMy,
}

const messages: Record<string, any> = {
  'zh-CN': zhCN,
  'en-US': enUS,
  'th-TH': thTH,
  'vi-VN': viVN,
  'id-ID': idID,
  'ms-MY': msMY,
}

export const SUPPORTED_LOCALES = localeOptions.map((o) => o.value)

/** 默认语言：本地已选 > 浏览器语言 > 中文 */
function getDefaultLocale(): string {
  const saved = localStorage.getItem('locale')
  if (saved && SUPPORTED_LOCALES.includes(saved)) return saved

  const nav = (navigator.language || '').toLowerCase()
  if (nav.startsWith('zh')) return 'zh-CN'
  // 精确匹配前缀（th / vi / id / ms / en）
  const hit = SUPPORTED_LOCALES.find((l) => nav.startsWith(l.slice(0, 2).toLowerCase()))
  return hit || 'en-US'
}

const i18n = createI18n({
  legacy: false,
  locale: getDefaultLocale(),
  // 兜底用英文而不是中文：小语种万一漏词，显示英文比显示中文更有用
  fallbackLocale: 'en-US',
  messages,
})

export default i18n

export function getElementPlusLocale(locale: string) {
  return elementPlusLocales[locale] || enUs
}

export function getLocaleIcon(locale: string): string {
  return localeOptions.find((l) => l.value === locale)?.icon || 'EN'
}

export function getLocaleLabel(locale: string): string {
  return localeOptions.find((l) => l.value === locale)?.label || locale
}
