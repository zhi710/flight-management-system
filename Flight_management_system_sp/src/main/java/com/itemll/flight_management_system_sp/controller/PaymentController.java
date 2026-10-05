package com.itemll.flight_management_system_sp.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.PaymentCreateDTO;
import com.itemll.flight_management_system_sp.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付控制器
 * <p>注意回调接口 {@link #paymentCallback} 的返回类型是 {@code String} 而不是 {@code Result}，
 * 这是渠道协议要求，详见方法注释。</p>
 */
@Slf4j
@Tag(name = "支付模块", description = "创建支付、查询支付状态、接收渠道回调")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    /** 渠道要求的成功应答内容，必须是纯文本，多一个字符都会被判定为失败 */
    private static final String CALLBACK_SUCCESS = "success";
    /** 渠道要求的失败应答内容 */
    private static final String CALLBACK_FAILURE = "failure";

    private final PaymentService paymentService;

    @Operation(summary = "创建支付")
    @PostMapping
    public Result<Map<String, Object>> createPayment(@Valid @RequestBody PaymentCreateDTO dto,
                                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(paymentService.createPayment(dto, userId));
    }

    @Operation(summary = "查询支付状态")
    @GetMapping("/{paymentId}")
    public Result<Map<String, Object>> getPaymentStatus(@PathVariable String paymentId,
                                                         HttpServletRequest request) {
        // 带上当前用户：支付单号是可被枚举的字符串，不校验归属等于把别人的订单交给任何登录用户
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(paymentService.getPaymentStatus(paymentId, userId));
    }

    /**
     * 为「改签补款」创建支付。
     *
     * <p>改签审核通过但手续费与差价合计为正时，改签单进入 {@code PENDING_PAYMENT}，
     * 旅客需要补款后改签才生效。这里复用整条支付链路，只是把支付单标记为
     * {@code biz_type=CHANGE}，入账时会走改签生效而不是推进订单状态。</p>
     *
     * <p>与 {@code POST /payments} 分开而不是合并的原因：那个接口的入参是订单号，
     * 且强校验「订单处于待支付 + 金额等于订单总额」，改签补款两条都不满足。</p>
     */
    @Operation(summary = "创建改签补款支付")
    @PostMapping("/change/{changeId}")
    public Result<Map<String, Object>> createChangePayment(@PathVariable String changeId,
                                                            @RequestBody(required = false) Map<String, Object> body,
                                                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String payMethod = body == null || body.get("payMethod") == null
                ? null : String.valueOf(body.get("payMethod"));
        return Result.ok(paymentService.createChangePayment(changeId, payMethod, userId));
    }

    /**
     * 第三方支付渠道异步回调。
     *
     * <p><b>三个必须遵守的约束：</b></p>
     * <ol>
     *   <li><b>返回纯文本。</b>支付宝只认纯文本 {@code "success"}；返回本项目统一的
     *       {@code Result} JSON 信封会被判定为处理失败，渠道会按 4m/10m/10m/1h… 的策略
     *       持续重推，最长重推 24 小时。因此本方法签名刻意与其它接口不同。</li>
     *   <li><b>无 Token。</b>渠道服务器不会携带 JWT，该路径已在
     *       {@code JwtAuthFilter.WHITE_LIST} 中放行；请求真伪完全由渠道验签保证。</li>
     *   <li><b>异常也要应答。</b>这里兜住所有异常并返回 {@code failure}，
     *       避免抛出让容器返回 500 页面 —— 渠道侧只能看到「连接正常但响应非法」，
     *       排查时缺少任何有用线索。</li>
     * </ol>
     */
    @Operation(summary = "支付渠道异步回调（第三方调用，返回纯文本）")
    @PostMapping("/callback/{provider}")
    public String paymentCallback(@PathVariable String provider, HttpServletRequest request) {
        Map<String, String> params = extractCallbackParams(request);
        boolean accepted;
        try {
            accepted = paymentService.handleCallback(provider, params);
        } catch (Exception e) {
            log.error("支付回调处理异常: provider={}", provider, e);
            accepted = false;
        }
        return accepted ? CALLBACK_SUCCESS : CALLBACK_FAILURE;
    }

    @Operation(summary = "模拟支付完成（仅演示模式可用）")
    @PostMapping("/{paymentId}/simulate")
    public Result<Map<String, Object>> simulatePay(@PathVariable String paymentId,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(paymentService.simulatePay(paymentId, userId));
    }

    /**
     * 提取回调参数。
     *
     * <p>支付宝异步通知是 {@code application/x-www-form-urlencoded}，Servlet 容器已经解析到
     * parameterMap 中，直接取即可。可选兼容 JSON 报文，是为了本地用 Postman / curl 手工联调
     * （JSON 报文同样会走渠道验签，验不过一样被拒绝，不存在绕过风险）。</p>
     */
    private Map<String, String> extractCallbackParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null) {
            parameterMap.forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    params.put(key, values[0]);
                }
            });
        }
        if (!params.isEmpty()) {
            return params;
        }

        // 表单参数已解析时请求体流已被容器消费，只有 JSON 报文才会走到这里
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
            String body = reader.lines().collect(Collectors.joining("\n")).trim();
            if (body.isEmpty()) {
                return params;
            }
            if (body.startsWith("{")) {
                JSONObject json = JSON.parseObject(body);
                json.forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
            } else {
                for (String pair : body.split("&")) {
                    int idx = pair.indexOf('=');
                    if (idx <= 0) {
                        continue;
                    }
                    params.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            log.warn("读取支付回调报文失败: provider={}", e.getMessage());
        }
        return params;
    }
}
