package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 座位 */
@Data
@TableName("seat")
public class Seat {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long flightId;
    private Integer rowNum;
    private String colCode;
    private String cabinClass;
    private String seatType;
    private BigDecimal extraFee;
    private String status;
    private Long passengerId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
