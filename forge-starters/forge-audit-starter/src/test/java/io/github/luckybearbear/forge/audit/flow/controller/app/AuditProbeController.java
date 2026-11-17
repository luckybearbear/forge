package io.github.luckybearbear.forge.audit.flow.controller.app;

import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计装配探针端点（controller.app → /app-api 双端前缀）：四条路径分别驱动访问日志的
 * 带体/无体、错误日志的 500 兜底、业务分支不入错误表四类用例。
 */
@RestController
public class AuditProbeController {

    record EchoRequest(String name) {}

    @PostMapping("/audit/echo")
    Result<String> echo(@RequestBody EchoRequest request) {
        return Result.ok(request.name()); // @RequestBody 读取驱动 ContentCachingRequestWrapper 缓存体
    }

    /** 二进制 body 用例载体：text/plain 1:1 字节读（ISO-8859-1）——0x00 原样进入缓存体与摘要。 */
    @PostMapping(value = "/audit/raw", consumes = MediaType.TEXT_PLAIN_VALUE)
    Result<Integer> raw(@RequestBody String body) {
        return Result.ok(body.length());
    }

    @GetMapping("/audit/ping")
    Result<String> ping() {
        return Result.ok("pong");
    }

    @GetMapping("/audit/boom")
    Result<Void> boom() {
        throw new IllegalStateException("audit-boom"); // → handleUnknown 500 → ErrorLogRecorded
    }

    @GetMapping("/audit/biz")
    Result<Void> biz() {
        throw BusinessException.of(ErrorCodes.NOT_FOUND, "audit-biz").withHttpStatus(404); // 业务分支：只入访问表
    }
}
