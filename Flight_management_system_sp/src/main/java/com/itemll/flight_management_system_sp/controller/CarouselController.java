package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.service.CarouselService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页轮播图控制器
 */
@Tag(name = "首页轮播图", description = "获取首页轮播图数据")
@RestController
@RequestMapping("/carousel")
@RequiredArgsConstructor
public class CarouselController {

    private final CarouselService carouselService;

    @Operation(summary = "获取轮播图列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getCarousels() {
        return Result.ok(carouselService.getActiveCarousels());
    }
}
