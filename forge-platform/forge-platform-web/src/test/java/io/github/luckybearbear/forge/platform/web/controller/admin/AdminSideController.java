package io.github.luckybearbear.forge.platform.web.controller.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用：真实包名含 {@code .controller.admin.} 以触发 admin 端前缀匹配（嵌套类无法构造该包名）。
 */
@RestController
public class AdminSideController {

    @GetMapping("/ping")
    public String ping() {
        return "admin";
    }
}
