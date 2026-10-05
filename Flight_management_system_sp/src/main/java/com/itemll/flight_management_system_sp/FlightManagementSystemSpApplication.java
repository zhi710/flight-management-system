package com.itemll.flight_management_system_sp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 航班管理系统启动类
 * <p>技术栈：Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis + JWT + SpringDoc</p>
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@MapperScan("com.itemll.flight_management_system_sp.mapper")
public class FlightManagementSystemSpApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightManagementSystemSpApplication.class, args);
    }
}
