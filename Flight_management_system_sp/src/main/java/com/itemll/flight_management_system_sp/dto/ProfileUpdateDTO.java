package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 更新个人信息请求 */
@Data
@Schema(description = "更新个人信息请求")
public class ProfileUpdateDTO {
    @Schema(description = "姓名")
    private String name;
    @Schema(description = "性别 MALE/FEMALE")
    private String gender;
    @Schema(description = "出生日期 yyyy-MM-dd")
    private String birthday;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "头像URL")
    private String avatar;
}
