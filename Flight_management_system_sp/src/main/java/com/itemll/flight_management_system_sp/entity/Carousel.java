package com.itemll.flight_management_system_sp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/** 首页轮播图 */
@Data
@TableName("carousel")
public class Carousel {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private Integer status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private java.time.LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private java.time.LocalDateTime updateTime;
}
