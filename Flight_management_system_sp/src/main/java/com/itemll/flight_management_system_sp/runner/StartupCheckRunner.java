package com.itemll.flight_management_system_sp.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 应用启动检查器
 * <p>在 Spring Boot 启动完成后自动执行，检查核心依赖服务的连接状态：</p>
 * <ul>
 *   <li>MySQL 数据库连接</li>
 *   <li>Redis 缓存连接</li>
 * </ul>
 * <p>检查结果会在控制台输出，方便开发者快速定位问题。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupCheckRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Environment environment;

    private static final String SEPARATOR = "=".repeat(60);
    private static final String SUB_SEPARATOR = "-".repeat(50);

    @Override
    public void run(ApplicationArguments args) {
        log.info(SEPARATOR);
        log.info("  航班管理系统 - 启动依赖检查");
        log.info(SEPARATOR);

        boolean allOk = true;

        // 1. 检查 MySQL
        allOk &= checkMySQL();

        // 2. 检查 Redis
        allOk &= checkRedis();

        // 3. 输出总结
        log.info(SEPARATOR);
        if (allOk) {
            log.info("  ✅ 所有依赖服务检查通过，系统启动完成！");
        } else {
            log.warn("  ⚠️ 部分依赖服务检查失败，请检查上方日志！");
        }
        log.info(SEPARATOR);

        // 4. 输出访问地址
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "/api");
        log.info("  接口文档地址: http://localhost:{}{}/swagger-ui.html", port, contextPath);
        log.info("  旅客端接口:   http://localhost:{}{}/", port, contextPath);
        log.info("  管理端接口:   http://localhost:{}{}/admin/", port, contextPath);
        log.info(SEPARATOR);
    }

    /**
     * 检查 MySQL 连接
     */
    private boolean checkMySQL() {
        log.info(SUB_SEPARATOR);
        log.info("  📦 检查 MySQL 数据库连接...");
        try {
            String url = environment.getProperty("spring.datasource.url", "未配置");
            String username = environment.getProperty("spring.datasource.username", "未配置");
            log.info("     地址: {}", url);
            log.info("     用户: {}", username);

            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(3)) {
                    String productName = connection.getMetaData().getDatabaseProductName();
                    String productVersion = connection.getMetaData().getDatabaseProductVersion();
                    log.info("  ✅ MySQL 连接成功！");
                    log.info("     数据库: {} {}", productName, productVersion);
                    return true;
                } else {
                    log.error("  ❌ MySQL 连接验证失败！");
                    return false;
                }
            }
        } catch (SQLException e) {
            log.error("  ❌ MySQL 连接失败: {}", e.getMessage());
            log.error("     请检查: 1) MySQL 服务是否启动  2) 数据库 {} 是否存在  3) 用户名密码是否正确",
                    environment.getProperty("spring.datasource.database", "flight_management"));
            return false;
        }
    }

    /**
     * 检查 Redis 连接
     */
    private boolean checkRedis() {
        log.info(SUB_SEPARATOR);
        log.info("  📦 检查 Redis 缓存连接...");
        try {
            String host = environment.getProperty("spring.data.redis.host", "localhost");
            String port = environment.getProperty("spring.data.redis.port", "6379");
            String database = environment.getProperty("spring.data.redis.database", "0");
            String password = environment.getProperty("spring.data.redis.password", "");
            log.info("     地址: {}:{}", host, port);
            log.info("     数据库: {}", database);
            log.info("     密码: {}", (password != null && !password.isEmpty()) ? "******" : "无");

            // 使用 connectionFactory 测试连接
            var connection = redisConnectionFactory.getConnection();
            String pong = new String(connection.ping());
            connection.close();

            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("  ✅ Redis 连接成功！(PING -> PONG)");

                // 测试读写
                String testKey = "startup:health:check";
                redisTemplate.opsForValue().set(testKey, "ok");
                Object value = redisTemplate.opsForValue().get(testKey);
                redisTemplate.delete(testKey);

                if ("ok".equals(String.valueOf(value))) {
                    log.info("  ✅ Redis 读写测试通过！");
                } else {
                    log.warn("  ⚠️ Redis 读写测试异常，值不匹配");
                }
                return true;
            } else {
                log.error("  ❌ Redis PING 响应异常: {}", pong);
                return false;
            }
        } catch (Exception e) {
            log.error("  ❌ Redis 连接失败: {}", e.getMessage());
            log.error("     请检查: 1) Redis 服务是否启动  2) 地址 {}:{} 是否正确",
                    environment.getProperty("spring.data.redis.host", "localhost"),
                    environment.getProperty("spring.data.redis.port", "6379"));
            log.error("     Windows 启动 Redis: redis-server.exe");
            log.error("     或使用 Docker: docker run -d -p 6379:6379 redis:latest");
            return false;
        }
    }
}
