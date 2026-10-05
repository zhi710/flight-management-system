package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ssr_code")
public class SsrCode {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String category;
    private String nameCn;
    private String nameEn;
    private String description;
    private String icon;
    private BigDecimal extraFee;
    private Integer sortOrder;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
