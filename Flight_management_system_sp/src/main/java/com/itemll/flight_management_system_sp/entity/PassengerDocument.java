package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 旅客证件 */
@Data
@TableName("passenger_document")
public class PassengerDocument {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String docType;
    private String docNumber;
    private String name;
    private String nationality;
    private LocalDate expireDate;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
