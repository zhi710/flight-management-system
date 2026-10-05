package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 常用旅客 */
@Data
@TableName("frequent_traveler")
public class FrequentTraveler {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String name;
    private String gender;
    private LocalDate birthday;
    private String idType;
    private String idNumber;
    private String phone;
    private String passengerType;
    private String frequentFlyerNo;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
