package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.util.FfpNoResolver;
import com.itemll.flight_management_system_sp.common.util.IdNumberValidator;
import com.itemll.flight_management_system_sp.dto.ProfileUpdateDTO;
import com.itemll.flight_management_system_sp.dto.RealnameDTO;
import com.itemll.flight_management_system_sp.entity.Airport;
import com.itemll.flight_management_system_sp.entity.Flight;
import com.itemll.flight_management_system_sp.entity.FrequentTraveler;
import com.itemll.flight_management_system_sp.entity.MemberMilesRecord;
import com.itemll.flight_management_system_sp.entity.Order;
import com.itemll.flight_management_system_sp.entity.OrderPassenger;
import com.itemll.flight_management_system_sp.entity.PassengerDocument;
import com.itemll.flight_management_system_sp.entity.User;
import com.itemll.flight_management_system_sp.mapper.AirportMapper;
import com.itemll.flight_management_system_sp.mapper.FlightMapper;
import com.itemll.flight_management_system_sp.mapper.FrequentTravelerMapper;
import com.itemll.flight_management_system_sp.mapper.MemberMilesRecordMapper;
import com.itemll.flight_management_system_sp.mapper.OrderPassengerMapper;
import com.itemll.flight_management_system_sp.mapper.PassengerDocumentMapper;
import com.itemll.flight_management_system_sp.mapper.UserMapper;
import com.itemll.flight_management_system_sp.service.MemberService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会员服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    /** 里程变动类型：累积 */
    private static final String TYPE_EARN = "EARN";

    /**
     * 舱位系数 —— 舱位越好，单位航段累积越多。
     * 与舱位费率（商务约为经济 2.4 倍）不同，里程系数刻意收敛，避免高舱位里程膨胀。
     */
    private static final Map<String, BigDecimal> CABIN_RATE = Map.of(
            "ECONOMY", new BigDecimal("1.0"),
            "BUSINESS", new BigDecimal("1.5"),
            "FIRST", new BigDecimal("2.0"));

    /** 等级加成 —— 高等级会员累积更快（行业通行的 elite bonus） */
    private static final Map<String, BigDecimal> LEVEL_BONUS = Map.of(
            "NORMAL", new BigDecimal("1.0"),
            "SILVER", new BigDecimal("1.1"),
            "GOLD", new BigDecimal("1.2"),
            "PLATINUM", new BigDecimal("1.5"));

    /** 机场缺经纬度时的兜底：按空中平均巡航速度 780 km/h ≈ 13 km/min 估算航段距离 */
    private static final long FALLBACK_KM_PER_MINUTE = 13L;

    /** 地球平均半径（km），用于大圆距离 */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final UserMapper userMapper;
    private final PassengerDocumentMapper documentMapper;
    private final FrequentTravelerMapper travelerMapper;
    private final MemberMilesRecordMapper milesRecordMapper;
    private final OrderPassengerMapper orderPassengerMapper;
    private final FfpNoResolver ffpNoResolver;
    private final FlightMapper flightMapper;
    private final AirportMapper airportMapper;

    @Override
    public Map<String, Object> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getUserId());
        result.put("name", user.getName());
        result.put("gender", user.getGender());
        result.put("birthday", user.getBirthday());
        result.put("phone", maskPhone(user.getPhone()));
        result.put("email", user.getEmail());
        result.put("avatar", user.getAvatar());
        result.put("memberLevel", user.getMemberLevel());
        result.put("miles", user.getMiles());
        result.put("memberNo", user.getMemberNo());
        // 证件号返回原文而不脱敏：个人中心的实名档案要原件回填到下单表单，
        // 返回掩码会把「110***********1234」当成证件号带进订单。
        // 该接口只返回当前登录用户自己的档案，不存在越权读取。
        result.put("idType", user.getIdType());
        result.put("idNumber", user.getIdNumber());
        result.put("realnameVerified", StrUtil.isNotBlank(user.getIdNumber()));
        result.put("realnameTime", user.getRealnameTime());
        return result;
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, ProfileUpdateDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (dto.getName() != null) user.setName(dto.getName());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getBirthday() != null) user.setBirthday(LocalDate.parse(dto.getBirthday()));
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void submitRealname(Long userId, RealnameDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "姓名不能为空");
        }
        String idType = (dto.getIdType() == null || dto.getIdType().isBlank())
                ? "ID_CARD" : dto.getIdType().trim().toUpperCase();
        String idNumber = dto.getIdNumber() == null ? "" : dto.getIdNumber().trim().toUpperCase();

        // 格式校验复用与建档/购票同一套规则，避免「这里存得进、那里过不了」
        String docError = IdNumberValidator.validate(idType, idNumber);
        if (docError != null) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID, docError);
        }

        // 一个证件号只能归属一个账号：否则同一个人可开多个账号重复领取会员权益
        Long occupied = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getIdNumber, idNumber)
                .ne(User::getId, userId));
        if (occupied != null && occupied > 0) {
            throw new BusinessException(ErrorCode.DOCUMENT_OCCUPIED,
                    "该证件号已被其他账号实名，请核对后重试");
        }

        // 首次实名时兜底认领「预发号」：家人先给此人建过常用旅客档案（可能没留手机号，
        // 注册阶段按手机号认领不到），此时拿到权威证件号，可以补认领一次，避免一人两号。
        // 只在首次实名做：已实名用户修改证件号时不该换号，号承载历史。
        // 安全性：未实名账号无法下单（REALNAME_REQUIRED 会拦），账号的号必然没有业务痕迹；
        // 且里程记录按 user_id 挂账（member_miles_record.user_id），认领后历史不会断。
        if (user.getRealnameTime() == null) {
            String preIssued = ffpNoResolver.findPreIssuedByDocument(idNumber);
            if (preIssued != null && !preIssued.equals(user.getMemberNo())) {
                log.info("实名时认领预发常旅客号: userId={}, {} -> {}",
                        userId, user.getMemberNo(), preIssued);
                user.setMemberNo(preIssued);
            }
        }

        // 姓名以实名提交为准：注册时填的可能只是昵称，实名后才是票面姓名
        user.setName(name);
        user.setIdType(idType);
        user.setIdNumber(idNumber);
        user.setRealnameTime(LocalDateTime.now());
        // 号一旦认领即固定：它代表里程归属，后续再改实名信息也不换号
        userMapper.updateById(user);
    }

    @Override
    public List<Map<String, Object>> getDocuments(Long userId) {
        List<PassengerDocument> docs = documentMapper.selectList(
                new LambdaQueryWrapper<PassengerDocument>()
                        .eq(PassengerDocument::getUserId, userId)
                        .eq(PassengerDocument::getDeleted, 0));
        return docs.stream().map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("docType", d.getDocType());
            m.put("docNumber", d.getDocNumber());
            m.put("name", d.getName());
            m.put("expireDate", d.getExpireDate());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public void addDocument(Long userId, Map<String, Object> doc) {
        String docType = (String) doc.get("docType");
        String docNumber = (String) doc.get("docNumber");
        // 证件号校验必须整体校验变更后的「类型 + 号码」，而不是只看传进来的那一个：
        // 只改了类型（身份证 → 护照）而号码不变时，号码本身可能在新类型下非法。
        requireValidDocument(docType, docNumber);
        PassengerDocument pd = new PassengerDocument();
        pd.setUserId(userId);
        pd.setDocType(docType);
        pd.setDocNumber(docNumber);
        pd.setName((String) doc.get("name"));
        pd.setNationality((String) doc.get("nationality"));
        if (doc.get("expireDate") != null) {
            pd.setExpireDate(LocalDate.parse((String) doc.get("expireDate")));
        }
        documentMapper.insert(pd);
    }

    @Override
    public void updateDocument(Long docId, Long userId, Map<String, Object> doc) {
        PassengerDocument pd = documentMapper.selectOne(
                new LambdaQueryWrapper<PassengerDocument>()
                        .eq(PassengerDocument::getId, docId)
                        .eq(PassengerDocument::getUserId, userId));
        if (pd == null) throw new BusinessException(ErrorCode.NOT_FOUND, "证件不存在");
        if (doc.containsKey("docType")) pd.setDocType((String) doc.get("docType"));
        if (doc.containsKey("docNumber")) pd.setDocNumber((String) doc.get("docNumber"));
        if (doc.containsKey("name")) pd.setName((String) doc.get("name"));
        if (doc.containsKey("nationality")) pd.setNationality((String) doc.get("nationality"));
        if (doc.containsKey("expireDate")) pd.setExpireDate(LocalDate.parse((String) doc.get("expireDate")));
        requireValidDocument(pd.getDocType(), pd.getDocNumber());
        documentMapper.updateById(pd);
    }

    @Override
    public void deleteDocument(Long docId, Long userId) {
        documentMapper.delete(
                new LambdaQueryWrapper<PassengerDocument>()
                        .eq(PassengerDocument::getId, docId)
                        .eq(PassengerDocument::getUserId, userId));
    }

    @Override
    public List<Map<String, Object>> getTravelers(Long userId) {
        List<FrequentTraveler> travelers = travelerMapper.selectList(
                new LambdaQueryWrapper<FrequentTraveler>()
                        .eq(FrequentTraveler::getUserId, userId)
                        .eq(FrequentTraveler::getDeleted, 0));
        return travelers.stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("gender", t.getGender());
            m.put("idType", t.getIdType());
            m.put("idNumber", t.getIdNumber());
            m.put("passengerType", t.getPassengerType());
            m.put("phone", t.getPhone());
            m.put("frequentFlyerNo", t.getFrequentFlyerNo());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public void addTraveler(Long userId, Map<String, Object> traveler) {
        String idType = (String) traveler.get("idType");
        String idNumber = (String) traveler.get("idNumber");
        // 证件号是常旅客号的第一匹配键：格式不正确就发号，等于把同一个人拆成多个会员
        requireValidDocument(idType, idNumber);
        boolean exists = travelerMapper.exists(
                new LambdaQueryWrapper<FrequentTraveler>()
                        .eq(FrequentTraveler::getUserId, userId)
                        .eq(FrequentTraveler::getIdType, idType)
                        .eq(FrequentTraveler::getIdNumber, idNumber)
                        .eq(FrequentTraveler::getDeleted, 0));
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT, "该旅客已存在");
        }
        FrequentTraveler ft = new FrequentTraveler();
        ft.setUserId(userId);
        ft.setName((String) traveler.get("name"));
        ft.setGender((String) traveler.get("gender"));
        ft.setIdType((String) traveler.get("idType"));
        ft.setIdNumber((String) traveler.get("idNumber"));
        ft.setPassengerType((String) traveler.getOrDefault("passengerType", "ADULT"));
        ft.setPhone((String) traveler.get("phone"));
        // 常旅客号由服务端按「人」签发（证件号 → 手机号 → 本人姓名 依次命中已有号），
        // 前端传入的 frequentFlyerNo 一律忽略：该号决定里程归属，允许自由输入等于允许刷里程
        ft.setFrequentFlyerNo(ffpNoResolver.resolveOrIssue(
                userId,
                (String) traveler.get("name"),
                (String) traveler.get("phone"),
                (String) traveler.get("idNumber")));
        travelerMapper.insert(ft);
    }

    @Override
    public void updateTraveler(Long travelerId, Long userId, Map<String, Object> traveler) {
        FrequentTraveler ft = travelerMapper.selectOne(
                new LambdaQueryWrapper<FrequentTraveler>()
                        .eq(FrequentTraveler::getId, travelerId)
                        .eq(FrequentTraveler::getUserId, userId));
        if (ft == null) throw new BusinessException(ErrorCode.NOT_FOUND, "常用旅客不存在");
        if (traveler.containsKey("name")) ft.setName((String) traveler.get("name"));
        if (traveler.containsKey("gender")) ft.setGender((String) traveler.get("gender"));
        if (traveler.containsKey("idType")) ft.setIdType((String) traveler.get("idType"));
        if (traveler.containsKey("idNumber")) ft.setIdNumber((String) traveler.get("idNumber"));
        if (traveler.containsKey("phone")) ft.setPhone((String) traveler.get("phone"));
        if (traveler.containsKey("passengerType")) ft.setPassengerType((String) traveler.get("passengerType"));
        // 证件类型与号码可能只传其一，故在全部赋值完成后再按最终值统一校验
        requireValidDocument(ft.getIdType(), ft.getIdNumber());
        // 常旅客号一经签发即终身不变，编辑档案时不接受任何号变更请求
        // （即便证件号被改，号也保持原值：号代表历史里程归属，换号会导致里程断层）
        travelerMapper.updateById(ft);
    }

    /**
     * 证件号统一收口校验，供四处写入点共用：
     * {@link #addTraveler}、{@link #updateTraveler}、{@link #addDocument}、{@link #updateDocument}。
     *
     * <p>抛出的 message 是面向用户的完整中文提示，前端可直接展示，无需再翻译。
     */
    private void requireValidDocument(String idType, String idNumber) {
        String error = IdNumberValidator.validate(idType, idNumber);
        if (error != null) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID, error);
        }
    }

    @Override
    public void deleteTraveler(Long travelerId, Long userId) {
        travelerMapper.delete(
                new LambdaQueryWrapper<FrequentTraveler>()
                        .eq(FrequentTraveler::getId, travelerId)
                        .eq(FrequentTraveler::getUserId, userId));
    }

    @Override
    public Map<String, Object> getMilesInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        List<MemberMilesRecord> records = milesRecordMapper.selectList(
                new LambdaQueryWrapper<MemberMilesRecord>()
                        .eq(MemberMilesRecord::getUserId, userId)
                        .orderByDesc(MemberMilesRecord::getCreateTime)
                        .last("LIMIT 20"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", user.getMiles());

        Map<String, Object> levelProgress = new HashMap<>();
        String currentLevel = user.getMemberLevel() == null ? "NORMAL" : user.getMemberLevel();
        String nextLevel = getNextLevel(currentLevel);
        int requiredMiles = getLevelMiles(nextLevel);
        int balance = user.getMiles() == null ? 0 : user.getMiles();
        levelProgress.put("current", currentLevel);
        levelProgress.put("next", nextLevel);
        levelProgress.put("required", requiredMiles);
        levelProgress.put("progress", levelProgressPercent(currentLevel, requiredMiles, balance));
        result.put("levelProgress", levelProgress);

        result.put("records", records.stream().map(r -> {
            Map<String, Object> rm = new HashMap<>();
            rm.put("date", r.getCreateTime().toLocalDate());
            rm.put("type", r.getType());
            rm.put("miles", r.getMiles());
            rm.put("description", r.getDescription());
            return rm;
        }).collect(Collectors.toList()));

        return result;
    }

    @Override
    @Transactional
    public void redeemMiles(Long userId, String type, String targetId, int miles) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getMiles() < miles) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "里程不足");
        }
        user.setMiles(user.getMiles() - miles);
        userMapper.updateById(user);

        MemberMilesRecord record = new MemberMilesRecord();
        record.setUserId(userId);
        record.setType("REDEEM");
        record.setMiles(-miles);
        record.setDescription("里程兑换: " + type + " " + targetId);
        milesRecordMapper.insert(record);
    }

    @Override
    @Transactional
    public int earnMilesForPaidOrder(Order order) {
        if (order == null || order.getId() == null) {
            return 0;
        }
        List<OrderPassenger> passengers = orderPassengerMapper.selectList(
                new LambdaQueryWrapper<OrderPassenger>()
                        .eq(OrderPassenger::getOrderId, order.getId()));
        if (passengers.isEmpty()) {
            return 0;
        }

        Flight flight = order.getFlightId() == null ? null : flightMapper.selectById(order.getFlightId());
        long distanceKm = resolveDistanceKm(flight);
        BigDecimal cabinRate = CABIN_RATE.getOrDefault(upper(order.getCabinClass()), BigDecimal.ONE);
        String route = flight == null
                ? ""
                : flight.getDepartureAirport() + "-" + flight.getArrivalAirport();

        int creditedMembers = 0;
        for (OrderPassenger passenger : passengers) {
            String flyerNo = passenger.getFrequentFlyerNo();
            if (flyerNo == null || flyerNo.isBlank()) {
                continue;
            }
            User member = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getMemberNo, flyerNo.trim()));
            if (member == null) {
                // 填的不是本平台号（例如外航常旅客号），不计入本平台里程
                continue;
            }
            // 幂等：支付回调可能重复投递，同一订单 + 同一会员只累积一次
            // （member_miles_record.uk_user_order_type 是数据库层最后一道防线）
            boolean alreadyEarned = milesRecordMapper.exists(new LambdaQueryWrapper<MemberMilesRecord>()
                    .eq(MemberMilesRecord::getUserId, member.getId())
                    .eq(MemberMilesRecord::getOrderId, order.getId())
                    .eq(MemberMilesRecord::getType, TYPE_EARN));
            if (alreadyEarned) {
                continue;
            }

            BigDecimal levelBonus = LEVEL_BONUS.getOrDefault(upper(member.getMemberLevel()), BigDecimal.ONE);
            int miles = calcMiles(distanceKm, cabinRate, levelBonus);
            if (miles <= 0) {
                continue;
            }

            MemberMilesRecord record = new MemberMilesRecord();
            record.setUserId(member.getId());
            record.setType(TYPE_EARN);
            record.setMiles(miles);
            record.setOrderId(order.getId());
            record.setDescription(StrUtil.maxLength(
                    order.getFlightNo() + " " + route + " " + cabinLabel(order.getCabinClass()), 200));
            milesRecordMapper.insert(record);

            int balance = (member.getMiles() == null ? 0 : member.getMiles()) + miles;
            member.setMiles(balance);
            member.setMemberLevel(levelOf(balance));
            userMapper.updateById(member);
            creditedMembers++;

            log.info("里程累积: userId={}, orderId={}, miles={}(距离{}km × 舱位{} × 等级{}), 余额={}, 等级={}",
                    member.getId(), order.getId(), miles, distanceKm, cabinRate, levelBonus,
                    balance, member.getMemberLevel());
        }
        return creditedMembers;
    }

    /** 里程 = 航段距离 × 舱位系数 × 等级加成 */
    private int calcMiles(long distanceKm, BigDecimal cabinRate, BigDecimal levelBonus) {
        if (distanceKm <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(distanceKm)
                .multiply(cabinRate)
                .multiply(levelBonus)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * 航段距离（km）：优先用起降机场经纬度算大圆距离（真实航段距离的做法）；
     * 机场缺坐标时退化为「计划飞行时长 × 平均巡航速度」，保证不因数据缺失而不发里程。
     */
    private long resolveDistanceKm(Flight flight) {
        if (flight == null) {
            return 0;
        }
        Airport departure = findAirport(flight.getDepartureAirport());
        Airport arrival = findAirport(flight.getArrivalAirport());
        boolean hasCoordinates = departure != null && arrival != null
                && departure.getLatitude() != null && departure.getLongitude() != null
                && arrival.getLatitude() != null && arrival.getLongitude() != null;
        if (hasCoordinates) {
            return Math.round(greatCircleKm(
                    departure.getLatitude().doubleValue(), departure.getLongitude().doubleValue(),
                    arrival.getLatitude().doubleValue(), arrival.getLongitude().doubleValue()));
        }
        if (flight.getDuration() != null && flight.getDuration() > 0) {
            return flight.getDuration().longValue() * FALLBACK_KM_PER_MINUTE;
        }
        return 0;
    }

    private Airport findAirport(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return airportMapper.selectOne(new LambdaQueryWrapper<Airport>().eq(Airport::getCode, code));
    }

    /** Haversine 大圆距离（km） */
    private double greatCircleKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private String cabinLabel(String cabinClass) {
        return switch (upper(cabinClass)) {
            case "BUSINESS" -> "公务舱";
            case "FIRST" -> "头等舱";
            default -> "经济舱";
        };
    }

    /** 按累计里程换算等级，阈值与 {@link #getLevelMiles} 保持一致 */
    private String levelOf(int miles) {
        if (miles >= getLevelMiles("PLATINUM")) return "PLATINUM";
        if (miles >= getLevelMiles("GOLD")) return "GOLD";
        if (miles >= getLevelMiles("SILVER")) return "SILVER";
        return "NORMAL";
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR, "原密码错误");
        }
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码长度为8-20位");
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
        log.info("用户修改密码成功: userId={}", userId);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String getNextLevel(String level) {
        return switch (level != null ? level : "NORMAL") {
            case "NORMAL" -> "SILVER";
            case "SILVER" -> "GOLD";
            case "GOLD" -> "PLATINUM";
            default -> "PLATINUM";
        };
    }

    private int getLevelMiles(String level) {
        return switch (level != null ? level : "") {
            case "SILVER" -> 5000;
            case "GOLD" -> 15000;
            case "PLATINUM" -> 50000;
            default -> 0;
        };
    }

    /**
     * 距下一等级的完成度（0-100），供会员中心的进度条使用。
     * 已到最高等级（没有下一级门槛）返回 100；其余在「本级门槛 → 下一级门槛」之间线性取值。
     * 此前该字段压根没返回，前端 {@code levelProgress.progress} 一直是 undefined，进度条是空的。
     */
    private int levelProgressPercent(String currentLevel, int requiredMiles, int balance) {
        if (requiredMiles <= 0) {
            return 100;
        }
        int currentThreshold = getLevelMiles(currentLevel);
        int span = requiredMiles - currentThreshold;
        if (span <= 0) {
            return 100;
        }
        int percent = (int) Math.round((balance - currentThreshold) * 100.0 / span);
        return Math.max(0, Math.min(100, percent));
    }
}
