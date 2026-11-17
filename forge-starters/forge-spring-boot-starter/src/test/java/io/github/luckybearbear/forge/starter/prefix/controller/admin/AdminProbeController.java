package io.github.luckybearbear.forge.starter.prefix.controller.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** starter 前缀装配测试载体：包名含 .controller.admin. → /admin-api 前缀。 */
@RestController
public class AdminProbeController {

    @GetMapping("/ping")
    public String ping() {
        return "admin";
    }
}
