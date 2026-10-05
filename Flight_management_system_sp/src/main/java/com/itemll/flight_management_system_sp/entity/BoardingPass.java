package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 登机牌 */
@Data
@TableName("boarding_pass")
public class BoardingPass {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long checkinId;
    private String passengerName;
    private String flightNo;
    private String seat;
    private String gate;
    private LocalDateTime boardingTime;
    private String qrCode;
    private String barcode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
