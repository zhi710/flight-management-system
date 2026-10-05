package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 订单旅客 */
@Data
@TableName("order_passenger")
public class OrderPassenger {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String passengerName;
    private String gender;
    private LocalDate birthday;
    private String idType;
    private String idNumber;
    private String phone;
    private String email;
    private String passengerType;
    private String frequentFlyerNo;
    private String ticketNo;
    private Integer seatRow;
    private String seatColumn;
    private String checkinStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
