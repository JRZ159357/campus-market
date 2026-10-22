// 文件路径: src/main/java/com/campus/market/CampusMarketApplication.java
package com.campus.market;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园二手交易平台启动类。
 * <p>
 * 开启定时任务：浏览量 Redis 增量批量回写 MySQL（见 ViewCountServiceImpl）。
 */
@EnableScheduling
@SpringBootApplication
@MapperScan("com.campus.market.mapper")
public class CampusMarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusMarketApplication.class, args);
    }
}
