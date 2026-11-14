package com.farm.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 数据库备份服务：调用 MySQL 自带的 mysqldump 把整个 farm_db 导成一个 .sql 文件。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    @Value("${farm.backup.dir}")   // 备份目录
    private String backupDir;

    @Value("${farm.backup.keep-days}")   // 保留天数
    private int keepDays;

    @Value("${farm.backup.mysql-bin}")   // mysqldump 所在目录
    private String mysqlBin;

    // 数据库账号直接复用数据源配置（spring.datasource.*）：全项目只维护一份，Java 里不再出现口令明文
    @Value("${spring.datasource.username}")   // 数据库账号
    private String dbUser;   // 【口令不硬编码】

    @Value("${spring.datasource.password}")   // 数据库密码
    private String dbPassword;

    // 数据源 URL：备份的库名从它里面取（jdbc:mysql://host:3306/库名?...），避免再配一项、也避免两处库名不一致
    @Value("${spring.datasource.url}")   // 数据库连接串
    private String datasourceUrl;

    /** 从数据源 URL 里解析出库名（去掉 ? 后面的参数，取最后一个 / 之后的部分）。 */
    private String dbName() {
        String url = datasourceUrl == null ? "" : datasourceUrl;
        int q = url.indexOf('?');
        if (q > 0) {
            url = url.substring(0, q);
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 执行一次全量备份。成功返回备份文件名（形如 farm_db_20260926_030000.sql），失败抛异常。 */
    public String backup() {   // 【mysqldump导出】
        try {
            // 备份目录不存在就建出来（带多级父目录）；建不出来只能报错
            File dir = new File(backupDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建备份目录：" + backupDir);
            }
            // 文件名带上 yyyyMMdd_HHmmss 时间戳，同一天备份多次也不会互相覆盖
            String fileName = "farm_db_" + STAMP.format(LocalDateTime.now()) + ".sql";
            Path out = new File(dir, fileName).toPath();
            // 账号来自配置（不再硬编码）；口令走 MYSQL_PWD 环境变量传给孩子进程，不出现在命令行参数里
            ProcessBuilder pb = new ProcessBuilder(
                    mysqlBin + "/mysqldump.exe",
                    "-u" + dbUser,
                    "--default-character-set=utf8mb4",
                    "--single-transaction", "--routines", "--triggers",
                    dbName());
            pb.environment().put("MYSQL_PWD", dbPassword);
            // 把 mysqldump 的输出（也就是整个 .sql 内容）直接重定向到备份文件
            pb.redirectOutput(out.toFile());
            // 错误流并到输出流：万一失败，报错信息也落在同一个文件里，方便下面读出来
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // 等外部进程跑完，取退出码（0 = 成功）
            int code = p.waitFor();
            if (code != 0) {
                // 退出码非 0 说明备份失败：把文件读出来当错误信息（里面是 mysqldump 的报错），再删掉这个半成品
                String err = Files.readString(out);
                Files.deleteIfExists(out);
                throw new IllegalStateException("mysqldump 退出码 " + code + "：" + err);
            }
            cleanup();
            return fileName;
        } catch (IOException | InterruptedException e) {
            // 等外部进程时被中断，把中断标记还回去，避免上层再也感知不到中断
            Thread.currentThread().interrupt();
            throw new IllegalStateException("备份失败：" + e.getMessage(), e);
        }
    }

    /** 清理过期备份：只扫备份目录里「名字以 farm_db_ 开头、以 .sql 结尾」的文件， */
    private void cleanup() throws IOException {   // 【按时间清7天】
        File dir = new File(backupDir);
        // 文件名过滤器：只认本项目生成的备份文件，别把目录里其他文件误删了
        File[] files = dir.listFiles((d, name) -> name.startsWith("farm_db_") && name.endsWith(".sql"));
        if (files == null) {
            return;
        }
        // 时间界线：当前毫秒时间戳 减去 保留天数换算成的毫秒数
        long cutoff = System.currentTimeMillis() - keepDays * 24 * 3600_000L;
        for (File f : files) {
            // 最后修改时间早于界线 = 超过保留天数
            if (f.lastModified() < cutoff) {
                Files.deleteIfExists(f.toPath());
                log.info("已清理过期备份：{}", f.getName());
            }
        }
    }

    /** 备份文件列表：扫备份目录，每个文件返回 名称 name / 大小 size（字节）/ 时间 time（毫秒时间戳）， */
    public List<Map<String, Object>> list() {
        File dir = new File(backupDir);
        File[] files = dir.listFiles((d, name) -> name.startsWith("farm_db_") && name.endsWith(".sql"));
        List<Map<String, Object>> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                list.add(Map.of("name", f.getName(), "size", f.length(), "time", f.lastModified()));
            }
            // 按 time 字段倒序：时间戳越大越新
            list.sort(Comparator.comparing(m -> (Long) ((Map<String, Object>) m).get("time")).reversed());
        }
        return list;
    }
}
