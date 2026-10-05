package com.itemll.flight_management_system_sp.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

/**
 * 分页返回结果
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页返回结果")
public class PageResult<T> implements Serializable {

    @Schema(description = "数据列表")
    private List<T> list;

    @Schema(description = "分页信息")
    private Pagination pagination;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "分页详情")
    public static class Pagination {
        @Schema(description = "当前页码")
        private long page;

        @Schema(description = "每页条数")
        private long pageSize;

        @Schema(description = "总记录数")
        private long total;

        @Schema(description = "总页数")
        private long totalPages;
    }

    /**
     * 快速构建分页结果
     */
    public static <T> PageResult<T> of(List<T> list, long page, long pageSize, long total) {
        long totalPages = (total + pageSize - 1) / pageSize;
        return new PageResult<>(list, new Pagination(page, pageSize, total, totalPages));
    }
}
