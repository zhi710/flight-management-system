package com.itemll.flight_management_system_sp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itemll.flight_management_system_sp.common.constants.ErrorCode;
import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.PageResult;
import com.itemll.flight_management_system_sp.entity.User;
import com.itemll.flight_management_system_sp.mapper.UserMapper;
import com.itemll.flight_management_system_sp.service.AdminMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端前台用户（旅客会员）管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMemberServiceImpl implements AdminMemberService {

    private final UserMapper userMapper;

    @Override
    public PageResult<Map<String, Object>> getMemberList(String phone, String name, String memberLevel,
                                                         Integer status, int page, int pageSize) {
        Page<User> pageObj = new Page<>(page, pageSize);
        Page<User> result = userMapper.selectPage(pageObj,
                new LambdaQueryWrapper<User>()
                        .like(StrUtil.isNotBlank(phone), User::getPhone, phone)
                        .like(StrUtil.isNotBlank(name), User::getName, name)
                        .eq(StrUtil.isNotBlank(memberLevel), User::getMemberLevel, memberLevel)
                        .eq(status != null, User::getStatus, status)
                        .orderByDesc(User::getCreateTime));

        List<Map<String, Object>> list = result.getRecords().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("userId", u.getUserId());
            m.put("phone", u.getPhone());
            m.put("name", u.getName());
            m.put("gender", u.getGender());
            m.put("email", u.getEmail());
            m.put("memberLevel", u.getMemberLevel());
            m.put("memberNo", u.getMemberNo());
            m.put("miles", u.getMiles());
            m.put("status", u.getStatus());
            m.put("createTime", u.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return PageResult.of(list, page, pageSize, result.getTotal());
    }

    @Override
    @Transactional
    public void updateMember(Long id, Map<String, Object> data) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        if (data.containsKey("name")) user.setName((String) data.get("name"));
        if (data.containsKey("email")) user.setEmail((String) data.get("email"));
        if (data.containsKey("memberLevel")) user.setMemberLevel((String) data.get("memberLevel"));
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void disableMember(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        user.setStatus(0);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void enableMember(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        user.setStatus(1);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void resetMemberPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        user.setPassword(BCrypt.hashpw("Reset@123"));
        userMapper.updateById(user);
        log.info("重置旅客密码: userId={}, phone={}", user.getUserId(), user.getPhone());
    }
}
