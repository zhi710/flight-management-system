/* ⚠️ 自动同步生成，请勿直接修改本文件。
   真源：design-system/palette.ts
   修改方式：编辑 design-system/theme.config.json → 运行 npm run theme:sync */

/**
 * Skynet Design System · 画布侧色板
 *
 * 由 design-system/gen-ramp.mjs 从 tokens.css 自动搬运生成，请勿手改。
 * 改品牌色：编辑 theme.config.json → 运行 npm run theme:sync
 *
 * 什么时候用它：ECharts、Canvas、以及任何在 <script> 里传色值的库 ——
 *   它们读不到 CSS 变量，只能拿十六进制。
 * 什么时候不要用它：DOM 上的样式一律写 var(--token)，那样才能跟随主题切换。
 */

export const tokens = {
  colorPrimary: '#1873CC',
  colorPrimaryHover: '#135CA3',
  colorPrimaryActive: '#0F4E8C',
  colorPrimarySoft: '#E8F1FA',
  colorPrimaryBorder: '#BAD5F0',
  colorPrimaryBright: '#1E90FF',
  sky300: '#5FB0FF',
  sky400: '#4DA3FF',
  sky500: '#1E90FF',
  sky600: '#1674D6',
  sky700: '#0A6EDD',
  sky800: '#0B5FB8',
  sky900: '#084C9E',
  colorSuccess: '#2E7D32',
  colorSuccessSoft: '#EAF2EB',
  colorWarning: '#96601A',
  colorWarningSoft: '#F5EFE8',
  colorDanger: '#C62828',
  colorDangerSoft: '#F9EAEA',
  colorInfo: '#5F6B7A',
  colorInfoSoft: '#EFF0F2',
  colorSuccessBorder: '#C0D8C2',
  colorWarningBorder: '#E0CFBA',
  colorDangerBorder: '#EEBFBF',
  colorInfoBorder: '#CFD3D7',
  colorSuccessSoftDeep: '#DCEEDA',
  colorWarningSoftDeep: '#F0E2CB',
  colorDangerSoftDeep: '#F4D4D4',
  colorInfoSoftDeep: '#DFE1E4',
  bgBoard: '#0B1220',
  textOnDark: '#E6EBF5',
  textOnDarkStrong: '#FFFFFF',
  bgOnDarkStrong: '#FFFFFF',
  textOnDarkSecondary: '#C3CFE0',
  textOnDarkMuted: '#9FB2C9',
  colorSuccessOnDark: '#4ADE80',
  colorWarningOnDark: '#FBBF24',
  colorDangerOnDark: '#F87171',
  tierNormalFg: '#5A6474',
  tierNormalBg: '#EDEFF3',
  tierSilverFg: '#646D7C',
  tierSilverBg: '#EEF1F5',
  tierGoldFg: '#8A6412',
  tierGoldBg: '#FAF0DC',
  tierPlatinumFg: '#1565C0',
  tierPlatinumBg: '#E6F0FB',
  tierDiamondFg: '#6D28D9',
  tierDiamondBg: '#EFE9FC',
  textPrimary: '#303133',
  textRegular: '#606266',
  textSecondary: '#5F6B7A',
  textPlaceholder: '#8F98A6',
  textDisabled: '#A8ABB2',
  textInverse: '#FFFFFF',
  bgPage: '#F5F7FA',
  bgCard: '#FFFFFF',
  bgHeader: '#FFFFFF',
  bgElevated: '#FFFFFF',
  bgMuted: '#F7F9FC',
  bgMutedStrong: '#EDF1F7',
  bgHover: '#F2F5F9',
  bgSidebar: '#1A2332',
} as const

export type TokenName = keyof typeof tokens

/** 图表系列色：顺序定义在 gen-ramp.mjs 的 CHART_SERIES，改色板只改那一行 */
export const chartPalette: string[] = [
  tokens.colorPrimary,
  tokens.colorSuccess,
  tokens.colorWarning,
  tokens.colorDanger,
  tokens.colorInfo,
  tokens.sky400,
]

/**
 * 色阶档位：某个语义色需要多个可区分的深浅时用它。
 * 典型场景是图表 —— 比如"飞行中 / 已到达 / 已完成"都是绿，但必须能分辨，
 * 于是取 light3 / light5。这些值不是手挑的，就是同一套派生公式算出来的，
 * 所以改基色时它们会一起走（原先页面里写死的 #95D475 / #B3E19D 就是这么过期的）。
 */
export const ramp = {
  primary: {
    light1: '#2F81D1',
    light2: '#468FD6',
    light3: '#5D9DDB',
    light4: '#74ABE0',
    light5: '#8CB9E6',
    light6: '#A3C7EB',
    light7: '#BAD5F0',
    light8: '#D1E3F5',
    light9: '#E8F1FA',
    dark2: '#135CA3',
  },
  success: {
    light1: '#438A47',
    light2: '#58975B',
    light3: '#6DA470',
    light4: '#82B184',
    light5: '#97BE99',
    light6: '#ABCBAD',
    light7: '#C0D8C2',
    light8: '#D5E5D6',
    light9: '#EAF2EB',
    dark2: '#256428',
  },
  warning: {
    light1: '#A17031',
    light2: '#AB8048',
    light3: '#B6905F',
    light4: '#C0A076',
    light5: '#CBB08D',
    light6: '#D5BFA3',
    light7: '#E0CFBA',
    light8: '#EADFD1',
    light9: '#F5EFE8',
    dark2: '#784D15',
  },
  danger: {
    light1: '#CC3D3D',
    light2: '#D15353',
    light3: '#D76969',
    light4: '#DD7E7E',
    light5: '#E39494',
    light6: '#E8A9A9',
    light7: '#EEBFBF',
    light8: '#F4D4D4',
    light9: '#F9EAEA',
    dark2: '#9E2020',
  },
  info: {
    light1: '#6F7A87',
    light2: '#7F8995',
    light3: '#8F97A2',
    light4: '#9FA6AF',
    light5: '#AFB5BD',
    light6: '#BFC4CA',
    light7: '#CFD3D7',
    light8: '#DFE1E4',
    light9: '#EFF0F2',
    dark2: '#4C5662',
  },
} as const

/** 按令牌名取色，名字写错时退回主色，避免图表整块变黑 */
export function tokenOf(name: TokenName): string {
  return tokens[name] ?? tokens.colorPrimary
}
