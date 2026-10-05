package com.itemll.flight_management_system_sp.common.util;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.entity.FrequentTraveler;
import com.itemll.flight_management_system_sp.entity.User;
import com.itemll.flight_management_system_sp.mapper.FrequentTravelerMapper;
import com.itemll.flight_management_system_sp.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 常旅客号解析器 —— **服务端唯一发号入口**。
 *
 * <p><b>为什么不能让前端传号</b>：常旅客号决定里程累积到哪个会员名下。若允许用户自由输入，
 * 任何人都能填入他人的号，把里程刷到别人（或自己）头上 —— 这是真实的越权风险。
 * 因此前端传来的 {@code frequentFlyerNo} 一律被忽略，号只由本类按「人」的身份解析出来。
 *
 * <p><b>号的归属口径（一人一号，号跟人走）</b>，按优先级依次尝试：
 * <ol>
 *   <li><b>证件号</b>命中已有实名账号或已有档案 → 复用其号。号属于「人」而非「账号」，
 *       所以跨用户匹配：A 给家人 B 建过档，B 自己注册后添加自己时拿到的仍是同一个号。
 *       匹配顺序：先查 {@code sys_user.id_number}（已实名的权威归属），
 *       再查 {@code frequent_traveler.id_number}（尚未注册但已被预发号）。</li>
 *   <li><b>手机号</b>命中某个 {@code sys_user} → 复用该账号的会员号（该人已有账号）。</li>
 *   <li><b>姓名</b>与当前登录账号一致 → 复用本人会员号（本人不该出现第二个号）。</li>
 *   <li>以上都不命中 → 由 {@link MemberNoGenerator} 签发新号。</li>
 * </ol>
 *
 * <p>第 4 种情况签发的号是「预发号」：该旅客尚无账号，号先落在其档案上；
 * 待其本人注册时，{@code AuthServiceImpl.register} 会按手机号命中该档案并复用此号，
 * 从而避免同一人拿到两个号。若建档时没留手机号、注册阶段认领不到，
 * {@code MemberServiceImpl.submitRealname} 会在**首次实名**时按证件号兜底认领。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FfpNoResolver {

    private final UserMapper userMapper;
    private final FrequentTravelerMapper travelerMapper;
    private final MemberNoGenerator memberNoGenerator;

    /**
     * 解析已有号，**不签发新号**。匹配不到返回 {@code null}。
     *
     * <p>用于下单等「不应产生副作用」的场景：旅客若确实从未建档，宁可不积里程，
     * 也不要在下单流程里凭空发号（否则号既没落库、又已被消耗，会与后续发号撞车）。
     */
    public String lookup(Long ownerUserId, String name, String phone, String idNumber) {
        String byIdNumber = findByDocument(idNumber);
        if (byIdNumber != null) {
            return byIdNumber;
        }
        String byPhone = findByAccountPhone(phone);
        if (byPhone != null) {
            return byPhone;
        }
        return findByOwnerName(ownerUserId, name);
    }

    /**
     * 解析号码；全部匹配不到时**签发新号**。
     *
     * <p>用于新增常用旅客等「建档」场景 —— 建档即发号，与主流航司一致。
     */
    public String resolveOrIssue(Long ownerUserId, String name, String phone, String idNumber) {
        String existed = lookup(ownerUserId, name, phone, idNumber);
        if (existed != null) {
            return existed;
        }
        String issued = memberNoGenerator.next();
        log.info("签发新常旅客号: no={}, name={}, phone={}", issued, name, mask(phone));
        return issued;
    }

    /**
     * 注册场景：为新账号确定会员号。
     *
     * <p>优先认领该手机号**已被预发**的号（他人给此人建过常用旅客档案时发过号），
     * 避免同一个人拿到两个号；确实没有才签发新号。
     */
    public String resolveForNewAccount(String phone) {
        String issued = findByAccountPhone(phone);
        if (issued != null) {
            return issued;
        }
        if (StrUtil.isNotBlank(phone)) {
            FrequentTraveler t = travelerMapper.selectOne(new LambdaQueryWrapper<FrequentTraveler>()
                    .eq(FrequentTraveler::getPhone, phone.trim())
                    .isNotNull(FrequentTraveler::getFrequentFlyerNo)
                    .ne(FrequentTraveler::getFrequentFlyerNo, "")
                    .orderByDesc(FrequentTraveler::getId)
                    .last("LIMIT 1"));
            if (t != null) {
                log.info("注册认领已预发的常旅客号: no={}, phone={}", t.getFrequentFlyerNo(), mask(phone));
                return t.getFrequentFlyerNo();
            }
        }
        return memberNoGenerator.next();
    }

    /**
     * 证件号 → 常旅客号（跨用户：号属于人，不属于账号）。
     *
     * <p><b>必须同时查两张表，且 {@code sys_user} 优先</b>：实名之后，证件号的权威归属地是
     * {@code sys_user.id_number}。若只查 {@code frequent_traveler}，已实名用户在被他人建
     * 常用旅客档案时会「查不到自己」而拿到第二个号，且下单里程会被记到那份档案的号上。
     */
    private String findByDocument(String idNumber) {
        if (StrUtil.isBlank(idNumber)) {
            return null;
        }
        String byRealname = findByUserDocument(idNumber);
        return byRealname != null ? byRealname : findPreIssuedByDocument(idNumber);
    }

    /** 实名账号持有的号（最高优先级：实名是权威身份） */
    private String findByUserDocument(String idNumber) {
        User member = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getIdNumber, idNumber.trim())
                .isNotNull(User::getMemberNo)
                .ne(User::getMemberNo, "")
                .orderByAsc(User::getId)
                .last("LIMIT 1"));
        return member == null ? null : member.getMemberNo();
    }

    /**
     * 证件号命中的「预发号」—— 只查常用旅客表。
     *
     * <p>用于实名阶段**兜底认领**：家人先建档时若没留手机号，注册阶段按手机号认领会失败，
     * 拿到权威证件号后可以补认领一次，避免同一个人持有两个号。
     *
     * <p>刻意不过滤 {@code deleted} —— 档案删了号不回收，否则该人再次出现时会拿到第二个号。
     */
    public String findPreIssuedByDocument(String idNumber) {
        if (StrUtil.isBlank(idNumber)) {
            return null;
        }
        FrequentTraveler t = travelerMapper.selectOne(new LambdaQueryWrapper<FrequentTraveler>()
                .eq(FrequentTraveler::getIdNumber, idNumber.trim())
                .isNotNull(FrequentTraveler::getFrequentFlyerNo)
                .ne(FrequentTraveler::getFrequentFlyerNo, "")
                .orderByDesc(FrequentTraveler::getId)
                .last("LIMIT 1"));
        return t == null ? null : t.getFrequentFlyerNo();
    }

    /** 手机号命中的账号会员号 */
    private String findByAccountPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return null;
        }
        User member = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone.trim())
                .isNotNull(User::getMemberNo)
                .ne(User::getMemberNo, "")
                .orderByAsc(User::getId)
                .last("LIMIT 1"));
        return member == null ? null : member.getMemberNo();
    }

    /** 姓名与当前登录账号一致的，视为本人，复用本人号 */
    private String findByOwnerName(Long ownerUserId, String name) {
        if (ownerUserId == null || StrUtil.isBlank(name)) {
            return null;
        }
        User owner = userMapper.selectById(ownerUserId);
        if (owner == null || !name.trim().equals(owner.getName())) {
            return null;
        }
        return StrUtil.isBlank(owner.getMemberNo()) ? null : owner.getMemberNo();
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 8) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
