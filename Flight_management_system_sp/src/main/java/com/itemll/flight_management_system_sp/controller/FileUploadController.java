package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.exception.BusinessException;
import com.itemll.flight_management_system_sp.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文件上传控制器
 * <p>支持图片（jpg/jpeg/png/gif/webp）和视频（mp4/avi/mov）上传</p>
 */
@Slf4j
@RestController
@RequestMapping("/file")
@Tag(name = "文件上传", description = "文件上传接口")
public class FileUploadController {

    @Value("${upload.base-dir:D:\\uploads\\images}")
    private String baseDir;

    @Value("${upload.url-prefix:/images}")
    private String urlPrefix;

    @Value("${upload.max-size:52428800}")
    private long maxSize; // 默认50MB

    /** 允许的文件类型 */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            // 图片
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            // 视频
            "mp4", "avi", "mov", "wmv", "flv", "mkv"
    ));

    /** 文件类型分组 */
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp"
    ));

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "avi", "mov", "wmv", "flv", "mkv"
    ));

    /**
     * 上传单个文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传图片或视频文件，返回访问URL")
    public Result<Map<String, Object>> upload(
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        // 1. 校验文件
        validateFile(file);

        // 2. 获取文件信息
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String fileType = IMAGE_EXTENSIONS.contains(extension.toLowerCase()) ? "image" : "video";

        // 3. 生成存储路径（按日期分目录）
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = dateDir + "/" + newFileName;

        // 4. 创建目录并保存文件
        File destFile = new File(baseDir, relativePath);
        if (!destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
        }

        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new BusinessException(500, "文件上传失败");
        }

        // 5. 构建访问URL
        String baseUrl = getBaseUrl(request);
        String relativeUrl = urlPrefix + "/" + relativePath.replace("\\", "/");
        String accessUrl = baseUrl + relativeUrl;

        // 6. 返回结果
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", accessUrl);                    // 完整URL：http://localhost:8080/api/images/2026/06/18/xxx.jpg
        data.put("path", relativeUrl);                 // 后半段路径：/images/2026/06/18/xxx.jpg
        data.put("filename", originalFilename);
        data.put("newFilename", newFileName);
        data.put("fileType", fileType);
        data.put("extension", extension);
        data.put("size", file.getSize());

        log.info("文件上传成功: {} -> {}", originalFilename, accessUrl);
        return Result.ok("上传成功", data);
    }

    /**
     * 批量上传文件
     */
    @PostMapping("/upload/batch")
    @Operation(summary = "批量上传文件", description = "批量上传图片或视频文件")
    public Result<List<Map<String, Object>>> batchUpload(
            @Parameter(description = "文件列表") @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {

        if (files == null || files.length == 0) {
            return Result.badRequest("请选择文件");
        }

        if (files.length > 10) {
            return Result.badRequest("单次最多上传10个文件");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> item = uploadSingleFile(file, request);
            results.add(item);
        }

        return Result.ok("上传成功", results);
    }

    /**
     * 上传单个文件（内部方法）
     */
    private Map<String, Object> uploadSingleFile(MultipartFile file, HttpServletRequest request) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String fileType = IMAGE_EXTENSIONS.contains(extension.toLowerCase()) ? "image" : "video";

        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String newFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = dateDir + "/" + newFileName;

        File destFile = new File(baseDir, relativePath);
        if (!destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
        }

        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new BusinessException(500, "文件上传失败: " + originalFilename);
        }

        String baseUrl = getBaseUrl(request);
        String relativeUrl = urlPrefix + "/" + relativePath.replace("\\", "/");
        String accessUrl = baseUrl + relativeUrl;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", accessUrl);                    // 完整URL
        data.put("path", relativeUrl);                 // 后半段路径
        data.put("filename", originalFilename);
        data.put("newFilename", newFileName);
        data.put("fileType", fileType);
        data.put("extension", extension);
        data.put("size", file.getSize());

        return data;
    }

    /**
     * 校验文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }

        // 校验文件大小
        if (file.getSize() > maxSize) {
            throw new BusinessException(400, "文件大小不能超过" + (maxSize / 1024 / 1024) + "MB");
        }

        // 校验文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new BusinessException(400, "文件名不能为空");
        }

        String extension = getExtension(filename);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(400, "不支持的文件类型，允许的类型: " + ALLOWED_EXTENSIONS);
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf(".");
        if (lastDot < 0) return "";
        return filename.substring(lastDot + 1);
    }

    /**
     * 获取基础URL
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();

        // 标准端口不拼接端口号
        if (("http".equals(scheme) && serverPort == 80) ||
            ("https".equals(scheme) && serverPort == 443)) {
            return scheme + "://" + serverName + contextPath;
        }
        return scheme + "://" + serverName + ":" + serverPort + contextPath;
    }
}
