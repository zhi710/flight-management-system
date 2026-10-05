package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/** 提交投诉建议请求 */
@Data
@Schema(description = "提交投诉建议请求")
public class FeedbackDTO {
    @NotBlank(message = "类型不能为空")
    @Schema(description = "类型 COMPLAINT/SUGGESTION")
    private String type;

    @Schema(description = "关联订单ID")
    private String orderId;

    @NotBlank(message = "内容不能为空")
    @Schema(description = "内容")
    private String content;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "附件URL列表")
    private List<String> attachments;
}
