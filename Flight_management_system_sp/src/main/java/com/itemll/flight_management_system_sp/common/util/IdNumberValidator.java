package com.itemll.flight_management_system_sp.common.util;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 证件号校验器 —— 证件号必须「长得对」才允许落库。
 *
 * <p><b>为什么必须校验</b>：证件号是常旅客号解析（{@link FfpNoResolver}）的第一匹配键，
 * 决定里程累积到哪个会员名下。在没有任何校验的情况下会同时出现两种破坏：
 * <ol>
 *   <li>同一个人用不同写法填证件号 → 被认成两个人 → 各自拿到一个常旅客号，里程被劈成两半；</li>
 *   <li>随手乱填的号一旦与真实旅客的号相撞 → 里程记到别人头上。</li>
 * </ol>
 * 因此证件号与常旅客号同属「服务端说了算」的字段，不接受未经校验的输入。
 *
 * <p><b>校验强度按证件类型分级</b>：
 * <ul>
 *   <li>{@code ID_CARD} —— 严格：18 位、前 17 位为 ASCII 数字、地区码属省级行政区、
 *       出生日期真实存在且落在 1900 年至今、第 18 位符合 GB 11643《公民身份号码》
 *       加权模 11-2 校验位算法。</li>
 *   <li>{@code PASSPORT} —— 字母开头的 6-20 位字母数字。</li>
 *   <li>{@code HM_PASSPORT} / {@code TAIWAN_PASSPORT} —— 5-20 位字母数字。</li>
 *   <li>其他类型 —— 2-64 位且不含空白字符。</li>
 * </ul>
 *
 * <p><b>边界说明</b>：本类只保证「格式正确、校验位自洽」，不保证号码真实存在。
 * 真伪核验需对接公安部实名认证接口，属于本类职责范围之外。
 */
public final class IdNumberValidator {

    private IdNumberValidator() {}

    /** GB 11643 加权因子（对应前 17 位） */
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    /** 余数 → 校验码映射表，下标为加权和 mod 11 */
    private static final char[] CHECK_CODES = "10X98765432".toCharArray();

    /** 省级行政区代码（身份证前 2 位） */
    private static final Set<String> PROVINCE_CODES = Set.of(
            "11", "12", "13", "14", "15",
            "21", "22", "23",
            "31", "32", "33", "34", "35", "36", "37",
            "41", "42", "43", "44", "45", "46",
            "50", "51", "52", "53", "54",
            "61", "62", "63", "64", "65",
            "71", "81", "82");

    /** 出生日期用 uuuu 而非 yyyy：配合 STRICT 解析，2 月 30 日这类非法日期会被直接拒绝 */
    private static final DateTimeFormatter BIRTH_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9]{5,19}$");
    private static final Pattern PERMIT_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,20}$");

    /**
     * 校验证件号。
     *
     * @param idType   证件类型（ID_CARD / PASSPORT / HM_PASSPORT / TAIWAN_PASSPORT / OTHER）
     * @param idNumber 证件号码，允许前后空格
     * @return {@code null} 表示通过；否则返回**面向用户的中文错误提示**，可直接抛出展示
     */
    public static String validate(String idType, String idNumber) {
        if (StrUtil.isBlank(idNumber)) {
            return "证件号码不能为空";
        }
        String number = idNumber.trim();
        String type = StrUtil.isBlank(idType) ? "OTHER" : idType.trim().toUpperCase();
        return switch (type) {
            case "ID_CARD" -> validateIdCard(number);
            case "PASSPORT" -> validatePassport(number);
            case "HM_PASSPORT", "TAIWAN_PASSPORT" -> validatePermit(number);
            default -> validateGeneric(number);
        };
    }

    /** 便捷判断，等价于 {@code validate(...) == null} */
    public static boolean isValid(String idType, String idNumber) {
        return validate(idType, idNumber) == null;
    }

    /**
     * 证件号脱敏：保留前 3 位与后 4 位。
     *
     * <p>注意：**本人自己的证件管理接口不应脱敏** —— 前端要用原件回填下单表单，
     * 返回掩码会导致提交时把掩码号写进订单。脱敏只用于日志与管理端列表。
     */
    public static String mask(String idNumber) {
        if (idNumber == null || idNumber.length() <= 7) {
            return idNumber;
        }
        return idNumber.substring(0, 3)
                + "*".repeat(idNumber.length() - 7)
                + idNumber.substring(idNumber.length() - 4);
    }

    // ==================== 分类型校验 ====================

    private static String validateIdCard(String number) {
        String v = number.toUpperCase();
        if (v.length() != 18) {
            return "身份证号应为 18 位，当前为 " + v.length() + " 位";
        }
        for (int i = 0; i < 17; i++) {
            if (!isAsciiDigit(v.charAt(i))) {
                return "身份证号前 17 位必须全部是数字";
            }
        }
        char last = v.charAt(17);
        if (!isAsciiDigit(last) && last != 'X') {
            return "身份证号末位只能是数字或字母 X";
        }
        if (!PROVINCE_CODES.contains(v.substring(0, 2))) {
            return "身份证号的地区码不合法（前 2 位）";
        }
        String birth = v.substring(6, 14);
        try {
            LocalDate date = LocalDate.parse(birth, BIRTH_FORMAT);
            if (date.isBefore(MIN_BIRTH_DATE)) {
                return "身份证号的出生日期不能早于 1900 年";
            }
            if (date.isAfter(LocalDate.now())) {
                return "身份证号的出生日期不能晚于今天";
            }
        } catch (DateTimeParseException e) {
            return "身份证号的出生日期不是合法日期（第 7-14 位）";
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (v.charAt(i) - '0') * WEIGHTS[i];
        }
        if (last != CHECK_CODES[sum % 11]) {
            return "身份证号的校验位不正确，请核对后重新填写";
        }
        return null;
    }

    private static String validatePassport(String number) {
        if (!PASSPORT_PATTERN.matcher(number).matches()) {
            return "护照号应为字母开头的 6-20 位字母或数字";
        }
        return null;
    }

    private static String validatePermit(String number) {
        if (!PERMIT_PATTERN.matcher(number).matches()) {
            return "通行证号应为 5-20 位字母或数字";
        }
        return null;
    }

    private static String validateGeneric(String number) {
        if (number.length() < 2 || number.length() > 64) {
            return "证件号长度应为 2-64 位";
        }
        for (int i = 0; i < number.length(); i++) {
            if (Character.isWhitespace(number.charAt(i))) {
                return "证件号不能包含空格";
            }
        }
        return null;
    }

    /** 刻意不用 Character.isDigit —— 它会把全角数字「１２３」也判为数字 */
    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
