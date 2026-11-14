package com.farm.platform.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 根路径跳转：把直接访问后端 8080 的浏览器请求 302 甩到前端登录页，免得看到一个报错页。 */
@RestController
public class RootController {

    @Value("${farm.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /** 收到 GET / 就返回 302 和 Location 响应头，浏览器自动跳到「前端地址 + /login」；本方法不返回页面内容。 */
    @GetMapping("/")
    public ResponseEntity<Void> redirectToLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/login"))
                .build();
    }
}
