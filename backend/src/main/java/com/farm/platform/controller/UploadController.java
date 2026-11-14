package com.farm.platform.controller;

import com.farm.platform.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 图片上传：存到 farm.upload-dir 并返回 /uploads/xxx；两道安全校验见下方 upload() 与 looksLikeImage() */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Value("${farm.upload-dir}")   // 上传根目录
    private String uploadDir;

    /** 允许的扩展名（前端 accept="image/*" 只是文件选择框过滤，可绕过，不能当校验） */
    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");

    /** 上传：校扩展名 + 校文件头 → 建目录 → 文件名换 UUID → 写盘。 */
    @PostMapping
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {   // 【上传三道关】
        // 校验①：取「最后一个点之后」并转小写；无扩展名时保持空串，一样会被拒。
        String ext = "";
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf('.')).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {   // 【追问·上传后缀】
            return Result.error(400, "只允许上传图片（jpg / jpeg / png / gif / webp / bmp）");
        }

        // 校验②：只读前 12 字节判文件头，不把整个文件读进内存
        if (!looksLikeImage(file, ext)) {
            return Result.error(400, "文件内容不是有效图片");
        }

        try {
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 文件名只由 UUID + 已白名单化的扩展名拼成，不含用户输入 ⇒ 落盘路径一定在上传目录内
            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            file.transferTo(new File(dir, fileName));
            return Result.ok(Map.of("fileName", name == null ? fileName : name, "url", "/uploads/" + fileName));
        } catch (Exception e) {
            return Result.error("上传失败：" + e.getMessage());
        }
    }

    /** 按文件头判断是不是该扩展名对应的图片：改后缀改不掉开头的固定字节；读不够或读异常一律判否 */
    private boolean looksLikeImage(MultipartFile file, String ext) {
        byte[] head = new byte[12];
        int n;
        // try-with-resources：正常还是异常都会关流
        try (InputStream in = file.getInputStream()) {
            n = in.read(head);
        } catch (Exception e) {
            return false;
        }
        if (n < 2) {
            return false;
        }
        // & 0xFF 是把 byte 转成 0~255 的无符号数再比，避免负数比较出错
        boolean jpeg = n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        boolean png = n >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
        boolean gif = n >= 4 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
        boolean bmp = (head[0] & 0xFF) == 0x42 && (head[1] & 0xFF) == 0x4D;   // "BM"
        boolean webp = n >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
        // 按扩展名去要求对应标识：不能拿 PNG 顶 .jpg（否则 Content-Type 与实际内容不符）
        return switch (ext) {
            case ".jpg", ".jpeg" -> jpeg;
            case ".png" -> png;
            case ".gif" -> gif;
            case ".bmp" -> bmp;
            case ".webp" -> webp;
            default -> false;
        };
    }
}
