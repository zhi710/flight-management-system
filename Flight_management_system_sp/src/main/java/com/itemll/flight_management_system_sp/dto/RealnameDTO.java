package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 实名认证请求。
 *
 * <p>实名信息是账号与「人」的绑定，也是购票的前置条件：
 * 姓名 + 证件类型 + 证件号三者共同确定一个人，
 * 订单中必须存在一位与该档案完全一致的乘客（本人）。
 *
 * <p>证件号的格式校验不放在注解里，而是交给
 * {@code IdNumberValidator} —— 不同证件类型规则不同，
 * 用一套正则既写不准也报不清原因。
 */
@Data
@Schema(description = "实名认证请求")
public class RealnameDTO {

    @NotBlank(message = "姓名不能为空")
    @Schema(description = "真实姓名（须与证件一致）", example = "张三")
    private String name;

    @NotBlank(message = "证件类型不能为空")
    @Schema(description = "证件类型 ID_CARD/PASSPORT/HM_PASSPORT/TAIWAN_PASSPORT/OTHER",
            example = "ID_CARD")
    private String idType;

    @NotBlank(message = "证件号不能为空")
    @Schema(description = "证件号码", example = "110101199001011237")
    private String idNumber;
}
