package com.itemll.flight_management_system_sp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itemll.flight_management_system_sp.entity.Carousel;
import com.itemll.flight_management_system_sp.mapper.CarouselMapper;
import com.itemll.flight_management_system_sp.service.CarouselService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarouselServiceImpl implements CarouselService {

    private final CarouselMapper carouselMapper;

    @Override
    public List<Map<String, Object>> getActiveCarousels() {
        List<Carousel> list = carouselMapper.selectList(
                new LambdaQueryWrapper<Carousel>()
                        .eq(Carousel::getStatus, 1)
                        .orderByAsc(Carousel::getSortOrder)
        );
        return list.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("subtitle", c.getSubtitle());
            map.put("imageUrl", c.getImageUrl());
            map.put("linkUrl", c.getLinkUrl());
            map.put("sortOrder", c.getSortOrder());
            return map;
        }).collect(Collectors.toList());
    }
}
