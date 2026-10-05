package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 系统运行时信息（公开接口，无需登录）
 * <p>每次应用启动生成一个实例标识 {@code instanceId}。前端开发模式轮询该接口，
 * 发现标识变化即说明后端已重新启动，自动刷新页面（见各前端 utils/devAutoReload.ts）。</p>
 */
@Tag(name = "系统信息")
@RestController
@RequestMapping("/system")
public class SystemController {

    /** 进程启动时生成，重启即变，用于前端识别“后端已重启” */
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    /** 进程启动时间，便于人工排查 */
    private static final LocalDateTime STARTED_AT = LocalDateTime.now();

    @Operation(summary = "获取本次启动的实例标识")
    @GetMapping("/instance")
    public Result<Map<String, Object>> instance() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instanceId", INSTANCE_ID);
        data.put("startedAt", STARTED_AT.toString());
        return Result.ok(data);
    }
}
