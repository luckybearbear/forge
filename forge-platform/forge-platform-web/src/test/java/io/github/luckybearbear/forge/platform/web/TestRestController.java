package io.github.luckybearbear.forge.platform.web;

import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestRestController {

    record EchoRequest(@NotBlank String name) {}

    @GetMapping("/test/ok")
    Result<String> ok() {
        return Result.ok("hello");
    }

    @GetMapping("/test/biz-404")
    Result<Void> biz404() {
        throw BusinessException.of(ErrorCodes.NOT_FOUND, "m-1").withHttpStatus(404);
    }

    @GetMapping("/test/biz-default")
    Result<Void> bizDefault() {
        throw new BusinessException(ErrorCodes.CONCURRENT_LOCKED);
    }

    @PostMapping("/test/validate")
    Result<String> validate(@Valid @RequestBody EchoRequest request) {
        return Result.ok(request.name());
    }

    @GetMapping("/test/boom")
    Result<Void> boom() {
        throw new IllegalStateException("boom");
    }
}
