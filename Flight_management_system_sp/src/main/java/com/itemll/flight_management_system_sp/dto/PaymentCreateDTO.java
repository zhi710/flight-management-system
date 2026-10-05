package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/** 创建支付请求 */
@Data
@Schema(description = "创建支付请求")
public class PaymentCreateDTO {
    @NotBlank(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private String orderId;

    @NotBlank(message = "支付方式不能为空")
    @Schema(description = "支付方式 WECHAT/ALIPAY/BANK_CARD/CREDIT")
    private String payMethod;

    @NotNull(message = "支付金额不能为空")
    @Schema(description = "支付金额")
    private BigDecimal amount;
}
