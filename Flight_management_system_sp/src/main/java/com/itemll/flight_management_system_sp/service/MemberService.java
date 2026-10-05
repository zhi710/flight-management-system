package com.itemll.flight_management_system_sp.service;

import com.itemll.flight_management_system_sp.dto.ProfileUpdateDTO;
import com.itemll.flight_management_system_sp.dto.RealnameDTO;
import com.itemll.flight_management_system_sp.entity.Order;
import java.util.List;
import java.util.Map;

/**
 * 会员服务接口
 */
public interface MemberService {

    /** 获取个人信息 */
    Map<String, Object> getProfile(Long userId);

    /** 更新个人信息 */
    void updateProfile(Long userId, ProfileUpdateDTO dto);

    /**
     * 提交或更新实名信息。
     *
     * <p>实名是账号与「人」的绑定，也是购票的前置条件。落库前校验三件事：
     * 姓名非空、证件号通过分类型格式校验、该证件号未被其他账号占用。
     *
     * <p>常旅客号不随实名变化：号承载历史里程，换号会导致里程断层，
     * 因此改实名只更新 id_type / id_number / name，不动 member_no。
     */
    void submitRealname(Long userId, RealnameDTO dto);

    /** 获取证件列表 */
    List<Map<String, Object>> getDocuments(Long userId);

    /** 添加证件 */
    void addDocument(Long userId, Map<String, Object> doc);

    /** 更新证件 */
    void updateDocument(Long docId, Long userId, Map<String, Object> doc);

    /** 删除证件 */
    void deleteDocument(Long docId, Long userId);

    /** 获取常用旅客列表 */
    List<Map<String, Object>> getTravelers(Long userId);

    /** 添加常用旅客 */
    void addTraveler(Long userId, Map<String, Object> traveler);

    /** 更新常用旅客 */
    void updateTraveler(Long travelerId, Long userId, Map<String, Object> traveler);

    /** 删除常用旅客 */
    void deleteTraveler(Long travelerId, Long userId);

    /** 里程查询 */
    Map<String, Object> getMilesInfo(Long userId);

    /** 里程兑换 */
    void redeemMiles(Long userId, String type, String targetId, int miles);

    /**
     * 为已支付订单累积里程。
     *
     * <p>口径：遍历订单旅客，凡 {@code frequent_flyer_no} 命中本平台常旅客号
     * （{@code sys_user.member_no}）的，按「航段距离 × 舱位系数 × 等级加成」为其发放里程，
     * 并同事务重算会员等级。已发过的（同一订单 + 同一会员）直接跳过，保证可重放。
     *
     * @return 实际获得里程的会员人数
     */
    int earnMilesForPaidOrder(Order order);

    /** 修改密码 */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
