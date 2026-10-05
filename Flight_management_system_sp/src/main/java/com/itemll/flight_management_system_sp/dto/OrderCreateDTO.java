package com.itemll.flight_management_system_sp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/** 创建订单请求 */
@Data
@Schema(description = "创建订单请求")
public class OrderCreateDTO {
    @Schema(description = "搜索ID（从搜索结果进入时携带，非搜索渠道可为空）")
    private String searchId;

    @NotBlank(message = "flightId不能为空")
    @Schema(description = "航班ID")
    private String flightId;

    @NotBlank(message = "cabinClass不能为空")
    @Schema(description = "舱位等级 ECONOMY/BUSINESS/FIRST")
    private String cabinClass;

    @NotEmpty(message = "旅客列表不能为空")
    @Valid
    @Schema(description = "旅客列表")
    private List<PassengerDTO> passengers;

    @Valid
    @Schema(description = "联系人信息")
    private ContactDTO contactInfo;

    @Schema(description = "附加服务列表")
    private List<ServiceDTO> services;

    @Data
    @Schema(description = "旅客信息")
    public static class PassengerDTO {
        @NotBlank(message = "旅客姓名不能为空")
        private String name;
        @NotBlank(message = "性别不能为空")
        private String gender;
        private String birthday;
        @NotBlank(message = "证件类型不能为空")
        private String idType;
        @NotBlank(message = "证件号码不能为空")
        private String idNumber;
        private String phone;
        private String email;
        private String frequentFlyerNo;
        @NotBlank(message = "旅客类型不能为空")
        private String passengerType;
    }

    @Data
    @Schema(description = "联系人信息")
    public static class ContactDTO {
        @NotBlank(message = "联系人姓名不能为空")
        private String name;
        @NotBlank(message = "联系电话不能为空")
        private String phone;
        private String email;
    }

    @Data
    @Schema(description = "附加服务")
    public static class ServiceDTO {
        private String type;
        private String code;
        private Integer quantity;
        private Integer passengerIndex;
    }
}
