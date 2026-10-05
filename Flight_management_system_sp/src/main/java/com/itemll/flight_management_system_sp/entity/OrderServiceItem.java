package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单附加服务 */
@Data
@TableName("order_service_item")
public class OrderServiceItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String serviceType;
    private String serviceCode;
    private String serviceName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Integer passengerIndex;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
