// 文件路径: src/main/java/com/campus/market/config/Knife4jConfig.java
package com.campus.market.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置。访问地址：http://localhost:8080/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI campusMarketOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("校园二手交易平台 接口文档")
                .version("1.0.0")
                .description("毕业设计项目接口文档。所有接口统一返回 Result<T>，成功 code=200。")
                .contact(new Contact().name("谢伟庆").email("202315020114@example.com")));
    }
}
