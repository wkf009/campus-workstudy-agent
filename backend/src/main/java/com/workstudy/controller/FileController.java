package com.workstudy.controller;

import com.workstudy.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileController {

    /** 允许上传的文件后缀白名单（简历/头像等常见类型） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".jpg", ".jpeg", ".png", ".gif", ".txt", ".md");

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public Result<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }

        // 类型白名单校验，防止上传可执行文件/脚本
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.error("不支持的文件类型: " + (extension.isEmpty() ? "(无后缀)" : extension));
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String newFilename = UUID.randomUUID().toString() + extension;
        String relativePath = dateDir + "/" + newFilename;

        try {
            Path uploadPath = Paths.get(uploadDir, dateDir);
            Files.createDirectories(uploadPath);
            file.transferTo(new File(uploadPath.toFile(), newFilename));

            Map<String, String> result = new HashMap<>();
            result.put("url", "/api/files/" + relativePath);
            result.put("filename", originalFilename);
            return Result.success("上传成功", result);
        } catch (IOException e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 读取上传文件：校验路径合法性，防止目录穿越（../）与非法文件名。
     */
    @GetMapping("/files/{dateDir}/{filename}")
    public byte[] getFile(@PathVariable String dateDir, @PathVariable String filename) throws IOException {
        if (dateDir == null || !dateDir.matches("\\d{8}")) {
            throw new IllegalArgumentException("非法的文件目录");
        }
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("非法的文件名");
        }
        Path filePath = Paths.get(uploadDir, dateDir, filename).normalize();
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!filePath.toAbsolutePath().normalize().startsWith(root)) {
            throw new IllegalArgumentException("非法的文件路径");
        }
        return Files.readAllBytes(filePath);
    }
}
