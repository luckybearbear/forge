package io.github.luckybearbear.forge.starter.prefix.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** starter 前缀装配测试载体：包名含 .controller.app. → /app-api 前缀。 */
@RestController
public class AppProbeController {

    @GetMapping("/ping")
    public String ping() {
        return "app";
    }
}
