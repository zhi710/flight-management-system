package com.itemll.flight_management_system_sp.config;

import com.itemll.flight_management_system_sp.security.AdminPermissionFilter;
import com.itemll.flight_management_system_sp.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>注册 JWT 认证过滤器和跨域策略。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthFilter jwtAuthFilter;
    private final AdminPermissionFilter adminPermissionFilter;

    @Value("${upload.base-dir:D:\\uploads\\images}")
    private String uploadBaseDir;

    @Value("${upload.url-prefix:/images}")
    private String urlPrefix;

    /**
     * 注册 JWT 过滤器
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 注册管理端权限过滤器（紧随 JWT 认证之后，按权限码拦截 /api/admin/**）
     */
    @Bean
    public FilterRegistrationBean<AdminPermissionFilter> adminPermissionFilterRegistration() {
        FilterRegistrationBean<AdminPermissionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(adminPermissionFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(2);
        return registration;
    }

    /**
     * 跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 静态资源映射
     * <p>将上传目录映射到 URL，使上传的文件可以通过 HTTP 访问</p>
     * <p>例如：D:uploads\images\2026\06\18\xxx.jpg → http://localhost:8080/api/images/2026/06/18/xxx.jpg</p>
     * <p>注意：由于 context-path 是 /api，所以资源处理器需要配置 /images/**</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /images/** 映射到本地上传目录
        // Spring Boot 的 context-path 会自动应用到资源处理器
        String location = "file:" + uploadBaseDir.replace("\\", "/");
        if (!location.endsWith("/")) {
            location += "/";
        }

        // 配置资源处理器（Spring Boot 会自动加上 context-path /api）
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(location);
    }
}
