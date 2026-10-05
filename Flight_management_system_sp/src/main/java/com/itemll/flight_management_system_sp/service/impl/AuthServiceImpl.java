package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.util.FfpNoResolver;
import com.itemll.flight_management_system_sp.config.SmsProperties;
import com.itemll.flight_management_system_sp.dto.LoginDTO;
import com.itemll.flight_management_system_sp.dto.RegisterDTO;
import com.itemll.flight_management_system_sp.entity.User;
import com.itemll.flight_management_system_sp.mapper.UserMapper;
import com.itemll.flight_management_system_sp.security.JwtUtil;
import com.itemll.flight_management_system_sp.security.RedisTokenService;
import com.itemll.flight_management_system_sp.service.AuthService;
import com.itemll.flight_management_system_sp.sms.SmsScene;
import com.itemll.flight_management_system_sp.sms.SmsSendResult;
import com.itemll.flight_management_system_sp.sms.SmsSenderRouter;
import com.itemll.flight_management_system_sp.sms.SmsThrottle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisTokenService redisTokenService;
    private final StringRedisTemplate redisTemplate;
    private final SmsSenderRouter smsSenderRouter;
    private final SmsThrottle smsThrottle;
    private final SmsProperties smsProperties;
    private final FfpNoResolver ffpNoResolver;

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    @Transactional
    public Map<String, Object> register(RegisterDTO dto) {
        // 检查手机号是否已注册
        User existUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (existUser != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该手机号已注册");
        }

        // 验证短信验证码（用途必须是 REGISTER，与发送时写入 Redis 的 key 一致）
        verifySmsCode(dto.getPhone(), dto.getSmsCode(), SmsScene.REGISTER);

        // 创建用户
        User user = new User();
        user.setUserId("U" + IdUtil.getSnowflakeNextIdStr());
        user.setPhone(dto.getPhone());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setName(dto.getName() != null ? dto.getName() : "用户" + dto.getPhone().substring(7));
        user.setMemberLevel("NORMAL");
        user.setMiles(0);
        user.setStatus(1);
        // 注册即发常旅客号（FF + 8 位顺序号，与 sys_user.uk_member_no 唯一索引配合保证不重号）。
        // 若该手机号此前已被他人建成常用旅客并预发过号，则认领那个号，避免同一人出现两个号。
        user.setMemberNo(ffpNoResolver.resolveForNewAccount(dto.getPhone()));
        userMapper.insert(user);

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getPhone());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 存入 Redis
        redisTokenService.saveUserToken(user.getId(), token, jwtUtil.validateToken(token) ? 7200000 : 7200000);

        log.info("用户注册成功: userId={}, phone={}", user.getUserId(), maskPhone(user.getPhone()));

        // 组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getUserId());
        result.put("phone", maskPhone(user.getPhone()));
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", 7200);
        return result;
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        // 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getPhone());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 存入 Redis（覆盖旧 Token，使旧 Token 失效）
        redisTokenService.saveUserToken(user.getId(), token, 7200000);

        log.info("用户登录成功: userId={}", user.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getUserId());
        result.put("name", user.getName());
        result.put("phone", maskPhone(user.getPhone()));
        result.put("avatar", user.getAvatar());
        result.put("memberLevel", user.getMemberLevel());
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", 7200);
        return result;
    }

    @Override
    public Map<String, Object> loginBySms(String phone, String smsCode) {
        // 用途必须是 LOGIN：改用例前后缀一致，短信验证码登录才真正走通
        verifySmsCode(phone, smsCode, SmsScene.LOGIN);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在，请先注册");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getPhone());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        redisTokenService.saveUserToken(user.getId(), token, 7200000);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getUserId());
        result.put("name", user.getName());
        result.put("phone", maskPhone(user.getPhone()));
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", 7200);
        return result;
    }

    @Override
    public int sendSmsCode(String phone, String type, String clientIp) {
        SmsScene scene = SmsScene.of(type);
        if (scene == SmsScene.RESET_PASSWORD) {
            // 接口尚未开放：明确拒绝，而不是发出一堆永远不会被校验的验证码
            throw new BusinessException(ErrorCode.CONFLICT, "重置密码功能暂未开放，敬请期待");
        }
        if (phone == null || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        String normalizedPhone = phone.trim();

        // 真实短信按条计费，先做业务前置校验，不为「注定失败」的请求买单
        precheckUser(normalizedPhone, scene);

        // 限流先于生成与发送：被挡下的请求不产生任何短信成本
        smsThrottle.acquire(normalizedPhone, clientIp);

        int expireMinutes = smsProperties.codeExpireMinutesOrDefault();
        String code = RandomUtil.randomNumbers(smsProperties.codeLengthOrDefault());
        String key = smsCodeKey(normalizedPhone, scene);

        try {
            // 先落 Redis 再发送：用户收到短信往往只需几秒，若发送成功才写库，
            // 会存在「短信已到、校验却读不到值」的竞态窗口
            redisTemplate.opsForValue().set(key, code, expireMinutes, TimeUnit.MINUTES);

            SmsSendResult result = smsSenderRouter.route().send(normalizedPhone, code, scene);
            if (!result.isSuccess()) {
                log.error("短信验证码发送失败: phone={}, scene={}, provider={}, providerCode={}, providerMessage={}",
                        maskPhone(normalizedPhone), scene.code(), result.getProvider(),
                        result.getProviderCode(), result.getProviderMessage());
                throw new BusinessException(ErrorCode.SMS_SEND_FAILED, "验证码发送失败，请稍后重试");
            }

            log.info("短信验证码已下发: phone={}, scene={}, provider={}, bizId={}, 有效期={}分钟",
                    maskPhone(normalizedPhone), scene.code(), result.getProvider(), result.getBizId(), expireMinutes);
            return expireMinutes * 60;

        } catch (RuntimeException e) {
            // 发送失败（渠道失败、凭证缺失、Redis 抖动）必须清理现场：
            // 否则 Redis 里会留下一份「用户永远收不到」的验证码，且用户要无谓地等 60 秒冷却
            rollbackSmsCode(key, normalizedPhone);
            throw e;
        }
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh Token 无效");
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        String newToken = jwtUtil.generateToken(user.getId(), user.getPhone());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        redisTokenService.saveUserToken(user.getId(), newToken, 7200000);

        Map<String, Object> result = new HashMap<>();
        result.put("token", newToken);
        result.put("refreshToken", newRefreshToken);
        result.put("expiresIn", 7200);
        return result;
    }

    @Override
    public void logout(Long userId) {
        redisTokenService.removeUserToken(userId);
        log.info("用户登出: userId={}", userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验短信验证码。
     *
     * <p><b>修复说明：</b>改造前这里把 Redis key 的后缀写死成 {@code :register}，
     * 而发送侧是按传入的 type 拼 key，于是「短信登录」写入 {@code sms:code:{手机号}:login}、
     * 校验却去读 {@code :register}，永远读不到值 —— 短信验证码登录实际是失效的，
     * 只有绕过校验的万能码能登进去。现在改为发送与校验共用 {@link #smsCodeKey}，
     * 从根上避免两侧写法不一致。</p>
     *
     * @param scene 用途，必须与发送时一致
     */
    private void verifySmsCode(String phone, String code, SmsScene scene) {
        // 演示兜底：命中万能码直接放行
        if (isUniversalCode(code)) {
            return;
        }

        // 防爆破：6 位数字只有 100 万种组合，没有失败次数限制就可以被脚本跑穿
        smsThrottle.checkVerifyAllowed(phone);

        String key = smsCodeKey(phone, scene);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null || !cached.equals(code)) {
            smsThrottle.markVerifyFailed(phone);
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR, "验证码错误或已过期");
        }

        // 一次性使用：校验通过立即删除，避免同一验证码被重复利用
        redisTemplate.delete(key);
        smsThrottle.clearVerifyFailed(phone);
    }

    /**
     * 是否为演示用万能码。
     * <p>命中时打 WARN —— 生产环境若出现这条日志，说明兜底开关忘了关。</p>
     */
    private boolean isUniversalCode(String code) {
        String universal = smsProperties.universalCode();
        if (universal == null || code == null || !universal.equals(code)) {
            return false;
        }
        log.warn("检测到使用演示万能验证码，已跳过短信校验（sms.demo.universal-code={}），"
                + "正式上线前请关闭该开关", universal);
        return true;
    }

    /** 验证码 Redis key 的唯一拼装入口：发送与校验必须走同一个方法 */
    private String smsCodeKey(String phone, SmsScene scene) {
        return SMS_CODE_PREFIX + phone + ":" + scene.code();
    }

    /**
     * 发送前的业务前置校验。
     * <p>真实短信按条计费，没必要为「注定失败」的请求买单：注册前先确认号码未被占用，
     * 登录前先确认账号存在，这样用户得到的也是更直接的提示。</p>
     *
     * <p><b>取舍：</b>这样做会让接口具备「探测手机号是否已注册」的能力。本项目是演示/毕设性质，
     * 且注册接口本身就会返回「该手机号已注册」，因此以省成本与即时提示为先；
     * 若面向公网生产环境，应改为「无论是否存在都返回同一句提示」。</p>
     */
    private void precheckUser(String phone, SmsScene scene) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (scene == SmsScene.REGISTER && user != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该手机号已注册，请直接登录");
        }
        if (scene != SmsScene.REGISTER && user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "该手机号尚未注册，请先注册");
        }
    }

    /** 发送失败后的现场清理：删掉验证码、释放冷却，让用户可以立即重试 */
    private void rollbackSmsCode(String redisKey, String phone) {
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("清理验证码失败: key={}, {}", redisKey, e.getMessage());
        }
        smsThrottle.release(phone);
    }

    /** 手机号脱敏 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
