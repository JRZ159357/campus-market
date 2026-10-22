// 文件路径: src/main/java/com/campus/market/controller/FileController.java
package com.campus.market.controller;

import com.campus.market.common.BusinessException;
import com.campus.market.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传接口。学生证、商品图共用。
 * 本地磁盘存储，按 yyyy/MM 分目录；生产可替换为对象存储，接口形态不变。
 */
@Slf4j
@Tag(name = "文件上传")
@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @Operation(summary = "上传图片，返回可直接访问的相对地址")
    @PostMapping("/upload")
    public Result<String> upload(
            @Parameter(description = "图片文件，最大 5MB，支持 jpg/png/gif/webp")
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("图片不能超过 5MB");
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (!StringUtils.hasText(ext) || !ALLOWED_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 jpg / png / gif / webp 格式");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase(Locale.ROOT);
        Path dir = Paths.get(uploadPath, datePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName).toFile());
        } catch (IOException e) {
            log.error("图片保存失败", e);
            throw new BusinessException("图片上传失败，请重试");
        }
        return Result.ok("/uploads/" + datePath + "/" + fileName);
    }
}
