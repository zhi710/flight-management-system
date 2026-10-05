import cn.hutool.crypto.digest.BCrypt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 压测数据准备 / 清理 / 校验。
 *
 * 数据库连接从环境变量读取，不落仓库：
 *   FMS_DB_URL   默认 jdbc:mysql://localhost:3306/flight_management?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
 *   FMS_DB_USER  默认 root
 *   FMS_DB_PASS  必填
 *
 * 用法（需 mysql 驱动 + hutool 在 classpath）：
 *   java -cp "<mysql.jar>;<hutool.jar>" LoadTestData.java setup
 *   java -cp "<mysql.jar>;<hutool.jar>" LoadTestData.java verify
 *   java -cp "<mysql.jar>;<hutool.jar>" LoadTestData.java cleanup
 *
 * 所有压测数据都带固定前缀/固定 ID 段，便于一次性回收：
 *   - 管理员 lt_admin        id 900000000000000101
 *   - 会员   13900000001     id 900000000000000201
 *   - 航班   LT9001..LT9030  id 900000000000000002 .. 900000000000000031
 */
public class LoadTestData {

    static final String URL = env("FMS_DB_URL",
            "jdbc:mysql://localhost:3306/flight_management"
                    + "?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
    static final String DB_USER = env("FMS_DB_USER", "root");
    static final String DB_PASS = env("FMS_DB_PASS", null);

    static final String ADMIN_USERNAME = "lt_admin";
    static final String MEMBER_PHONE = "13900000001";
    static final String PASSWORD = "LoadTest@2026";
    static final String ADMIN_NAME = "压测管理员"; // 压测管理员
    static final String MEMBER_NAME = "压测用户";      // 压测用户

    static final long ADMIN_ID = 900000000000000101L;
    static final long MEMBER_ID = 900000000000000201L;
    static final long FLIGHT_BASE = 900000000000000001L;
    static final int FLIGHT_COUNT = 30;
    static final String FLIGHT_DATE = "2026-09-15";
    static final String DEP = "PEK";
    static final String ARR = "SHA";

    /** 下单链路用到的子表（都含 order_id，按订单回收） */
    static final String[] ORDER_CHILD_TABLES = {
            "order_passenger", "order_service_item", "special_service_request",
            "order_change", "order_refund", "payment", "check_in", "feedback", "member_miles_record"
    };

    public static void main(String[] args) throws Exception {
        if (DB_PASS == null || DB_PASS.isEmpty()) {
            System.err.println("请先设置环境变量 FMS_DB_PASS");
            System.exit(2);
        }
        String cmd = args.length > 0 ? args[0] : "help";
        try (Connection c = DriverManager.getConnection(URL, DB_USER, DB_PASS)) {
            c.setAutoCommit(true);
            switch (cmd) {
                case "setup" -> {
                    cleanup(c);
                    setup(c);
                }
                case "cleanup" -> cleanup(c);
                case "verify" -> verify(c);
                default -> System.out.println("usage: setup | cleanup | verify");
            }
        }
    }

    static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    // ---------------------------------------------------------------- setup

    static void setup(Connection c) throws Exception {
        String hash = BCrypt.hashpw(PASSWORD);

        // 1) 压测管理员（挂 SUPER_ADMIN 角色，保证后台只读接口不被权限过滤器拦掉）
        exec(c, "INSERT INTO sys_admin_user (id, username, password, name, phone, email, status, deleted) "
                        + "VALUES (?,?,?,?,?,?,1,0)",
                ADMIN_ID, ADMIN_USERNAME, hash, ADMIN_NAME, "13900000002", "lt_admin@loadtest.local");
        Long roleId = queryLong(c, "SELECT id FROM sys_role WHERE code='SUPER_ADMIN' LIMIT 1");
        if (roleId == null) throw new IllegalStateException("sys_role 里找不到 SUPER_ADMIN");
        exec(c, "INSERT INTO sys_user_role (id, user_id, role_id) VALUES (?,?,?)",
                ADMIN_ID + 1, ADMIN_ID, roleId);

        // 2) 压测会员（登录拿 token 用）
        exec(c, "INSERT INTO sys_user (id, user_id, phone, password, name, member_level, member_no, miles, status, deleted) "
                        + "VALUES (?,?,?,?,?,'NORMAL',?,0,1,0)",
                MEMBER_ID, "LT0001", MEMBER_PHONE, hash, MEMBER_NAME, "LT20260909");

        // 3) 压测航班 + 舱位（库存给足，避免压测把座位抢空后全部报"座位不足"）
        String[] cabinClass = {"ECONOMY", "BUSINESS", "FIRST"};
        String[] cabinName = {"经济舱", "公务舱", "头等舱"}; // 经济舱/公务舱/头等舱
        BigDecimal[] fare = {new BigDecimal("800.00"), new BigDecimal("2400.00"), new BigDecimal("4800.00")};
        List<String> csv = new ArrayList<>();

        for (int i = 1; i <= FLIGHT_COUNT; i++) {
            long fid = FLIGHT_BASE + i;
            String flightNo = "LT" + (9000 + i);
            LocalDateTime dep = LocalDateTime.parse(FLIGHT_DATE + "T06:00:00").plusMinutes((i - 1) * 30L);
            LocalDateTime arr = dep.plusMinutes(120);

            exec(c, "INSERT INTO flight (id, flight_no, flight_date, departure_airport, arrival_airport, "
                            + "departure_terminal, arrival_terminal, departure_gate, departure_time, arrival_time, duration, "
                            + "airline_id, flight_type, status, checkin_status, checkin_open_hours, checkin_close_minutes, "
                            + "stops, meal_provided, wifi, power, version, deleted) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,1,0,0,0,0)",
                    fid, flightNo, FLIGHT_DATE, DEP, ARR, "T3", "T2", "L" + (i % 20),
                    Timestamp.valueOf(dep), Timestamp.valueOf(arr), 120,
                    1L, "DOMESTIC", "SCHEDULED", "NOT_OPEN", 24, 30);

            for (int k = 0; k < 3; k++) {
                long cid = FLIGHT_BASE + i * 10L + k + 1;
                BigDecimal total = fare[k].add(new BigDecimal("50.00"));
                exec(c, "INSERT INTO flight_cabin (id, flight_id, cabin_class, cabin_name, fare, tax, total_price, "
                                + "total_seats, available_seats, version, deleted) VALUES (?,?,?,?,?,?,?,?,?,0,0)",
                        cid, fid, cabinClass[k], cabinName[k], fare[k], new BigDecimal("50.00"), total,
                        100000, 100000);
                csv.add(fid + "," + cabinClass[k]);
            }
        }

        Path csvPath = Paths.get("data", "flights.csv");
        Files.createDirectories(csvPath.getParent());
        Files.write(csvPath, csv, StandardCharsets.UTF_8);

        System.out.println("SETUP OK");
        System.out.println("  admin  : " + ADMIN_USERNAME + " / " + PASSWORD + "  (id=" + ADMIN_ID + ", SUPER_ADMIN)");
        System.out.println("  member : " + MEMBER_PHONE + " / " + PASSWORD + "  (id=" + MEMBER_ID + ")");
        System.out.println("  flights: " + FLIGHT_COUNT + " 条 " + DEP + "->" + ARR + " " + FLIGHT_DATE
                + " (LT9001..LT9030, 每条 3 个舱位, 每舱 100000 座)");
        System.out.println("  csv    : " + csvPath.toAbsolutePath() + " (" + csv.size() + " 行)");
    }

    // -------------------------------------------------------------- cleanup

    static void cleanup(Connection c) throws Exception {
        long lo = FLIGHT_BASE + 1, hi = FLIGHT_BASE + FLIGHT_COUNT;
        int total = 0;
        // 订单子表 → 订单 → 舱位 → 航班
        for (String t : ORDER_CHILD_TABLES) {
            if (!tableExists(c, t)) continue;
            total += exec(c, "DELETE x FROM " + t + " x JOIN t_order o ON x.order_id = o.id "
                    + "WHERE o.flight_id BETWEEN ? AND ?", lo, hi);
        }
        total += exec(c, "DELETE FROM t_order WHERE flight_id BETWEEN ? AND ?", lo, hi);
        total += exec(c, "DELETE FROM seat WHERE flight_id BETWEEN ? AND ?", lo, hi);
        total += exec(c, "DELETE FROM flight_cabin WHERE flight_id BETWEEN ? AND ?", lo, hi);
        total += exec(c, "DELETE FROM flight WHERE id BETWEEN ? AND ?", lo, hi);
        // 账号
        total += exec(c, "DELETE FROM sys_user_role WHERE user_id = ?", ADMIN_ID);
        total += exec(c, "DELETE FROM sys_admin_user WHERE id = ?", ADMIN_ID);
        total += exec(c, "DELETE FROM sys_user WHERE id = ?", MEMBER_ID);
        // 后台只读接口会写操作日志，压测期间由 lt_admin 产生的一并清掉
        total += exec(c, "DELETE FROM sys_log WHERE operator_id = ?", ADMIN_ID);
        total += exec(c, "DELETE FROM sys_log WHERE operator_name = ?", String.valueOf(ADMIN_ID));
        System.out.println("CLEANUP OK, 删除行数 = " + total);
    }

    // --------------------------------------------------------------- verify

    static void verify(Connection c) throws Exception {
        long lo = FLIGHT_BASE + 1, hi = FLIGHT_BASE + FLIGHT_COUNT;
        System.out.println("-- 压测数据 --");
        System.out.println("flight      : " + queryLong(c, "SELECT COUNT(*) FROM flight WHERE id BETWEEN " + lo + " AND " + hi));
        System.out.println("flight_cabin: " + queryLong(c, "SELECT COUNT(*) FROM flight_cabin WHERE flight_id BETWEEN " + lo + " AND " + hi));
        System.out.println("orders      : " + queryLong(c, "SELECT COUNT(*) FROM t_order WHERE flight_id BETWEEN " + lo + " AND " + hi));
        System.out.println("order_pax   : " + queryLong(c, "SELECT COUNT(*) FROM order_passenger op JOIN t_order o ON op.order_id=o.id WHERE o.flight_id BETWEEN " + lo + " AND " + hi));
        System.out.println("admin       : " + queryLong(c, "SELECT COUNT(*) FROM sys_admin_user WHERE id = " + ADMIN_ID));
        System.out.println("member      : " + queryLong(c, "SELECT COUNT(*) FROM sys_user WHERE id = " + MEMBER_ID));
        System.out.println("sys_log(lt) : " + queryLong(c, "SELECT COUNT(*) FROM sys_log WHERE operator_id = " + ADMIN_ID));
        System.out.println("-- 全库 --");
        System.out.println("t_order 总量: " + queryLong(c, "SELECT COUNT(*) FROM t_order"));
        System.out.println("sys_log 总量: " + queryLong(c, "SELECT COUNT(*) FROM sys_log"));
    }

    // ---------------------------------------------------------------- utils

    static boolean tableExists(Connection c, String t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA='flight_management' AND TABLE_NAME=?")) {
            ps.setString(1, t);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static int exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        }
    }

    static Long queryLong(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }
}
