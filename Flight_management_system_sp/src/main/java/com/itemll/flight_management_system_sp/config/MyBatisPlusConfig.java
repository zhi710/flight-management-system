package com.itemll.flight_management_system_sp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * <ul>
 *   <li>分页插件</li>
 *   <li>乐观锁插件（配合 flight_cabin.version 实现并发控制）</li>
 * </ul>
 *
 * <p><b>关于 Mapper 扫描：</b>本类不再声明 {@code @MapperScan} ——
 * 启动类 {@code FlightManagementSystemSpApplication} 已经声明过一次，
 * 两处重复声明会让同一批 Mapper 被注册两遍，启动日志刷出 40 余行
 * 「Skipping MapperFactoryBean ... Bean already defined」与
 * 「No MyBatis mapper was found」警告。统一由启动类一处扫描。</p>
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件（MySQL）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 乐观锁插件：实体类中带 @Version 注解的字段会自动参与 CAS 更新
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
