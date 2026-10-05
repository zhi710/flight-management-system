/**
 * 证件号前端即时校验 —— 与后端 IdNumberValidator 保持同一套规则。
 *
 * 前端校验只为「即时反馈」，不作为安全边界：真正的拦截在后端，
 * 这里放过的任何输入后端仍会再校验一次。两侧规则必须同步修改。
 */

const WEIGHTS = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
const CHECK_CODES = '10X98765432'

/** 省级行政区代码（身份证前 2 位） */
const PROVINCE_CODES = new Set([
  '11', '12', '13', '14', '15',
  '21', '22', '23',
  '31', '32', '33', '34', '35', '36', '37',
  '41', '42', '43', '44', '45', '46',
  '50', '51', '52', '53', '54',
  '61', '62', '63', '64', '65',
  '71', '81', '82',
])

const PASSPORT_PATTERN = /^[A-Za-z][A-Za-z0-9]{5,19}$/
const PERMIT_PATTERN = /^[A-Za-z0-9]{5,20}$/

/** 出生日期必须真实存在（排除 2 月 30 日这类），且落在 1900 年至今 */
function isRealBirthDate(yyyymmdd: string): boolean {
  const year = Number(yyyymmdd.slice(0, 4))
  const month = Number(yyyymmdd.slice(4, 6))
  const day = Number(yyyymmdd.slice(6, 8))
  if (month < 1 || month > 12 || day < 1) return false
  const date = new Date(Date.UTC(year, month - 1, day))
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return false
  }
  return date.getTime() >= Date.UTC(1900, 0, 1) && date.getTime() <= Date.now()
}

/** 18 位身份证：校验位采用 GB 11643 加权模 11-2 算法 */
function checkIdCard(value: string): string | null {
  const v = value.toUpperCase()
  if (v.length !== 18) return 'validation.idNumberLength'
  for (let i = 0; i < 17; i++) {
    const c = v.charCodeAt(i)
    if (c < 48 || c > 57) return 'validation.idNumberFormat'
  }
  const last = v.charAt(17)
  if (!/^[0-9X]$/.test(last)) return 'validation.idNumberFormat'
  if (!PROVINCE_CODES.has(v.slice(0, 2))) return 'validation.idNumberRegion'
  if (!isRealBirthDate(v.slice(6, 14))) return 'validation.idNumberBirth'
  let sum = 0
  for (let i = 0; i < 17; i++) {
    sum += (v.charCodeAt(i) - 48) * (WEIGHTS[i] ?? 0)
  }
  if (last !== CHECK_CODES.charAt(sum % 11)) return 'validation.idNumberCheck'
  return null
}

/**
 * 校验证件号。
 * @returns i18n key（如 `validation.idNumberCheck`），通过时返回 `null`
 */
export function checkIdNumber(idType: string, idNumber: string): string | null {
  const value = (idNumber || '').trim()
  if (!value) return 'validation.idNumberEmpty'

  switch ((idType || 'OTHER').toUpperCase()) {
    case 'ID_CARD':
      return checkIdCard(value)
    case 'PASSPORT':
      return PASSPORT_PATTERN.test(value) ? null : 'validation.passport'
    case 'HM_PASSPORT':
    case 'TAIWAN_PASSPORT':
      return PERMIT_PATTERN.test(value) ? null : 'validation.permitFormat'
    default:
      if (value.length < 2 || value.length > 64) return 'validation.idNumberTooLong'
      return /\s/.test(value) ? 'validation.idNumberSpace' : null
  }
}

/** 各证件类型对应的格式说明，显示在输入框下方的小字提示里 */
export function idNumberRule(idType: string): string {
  switch ((idType || 'OTHER').toUpperCase()) {
    case 'ID_CARD':
      return 'validation.idCardRule'
    case 'PASSPORT':
      return 'validation.passportRule'
    case 'HM_PASSPORT':
    case 'TAIWAN_PASSPORT':
      return 'validation.permitRule'
    default:
      return 'validation.otherRule'
  }
}
