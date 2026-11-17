package io.github.luckybearbear.forge.demo.mold.controller.app;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.demo.mold.api.RegisterMoldRequest;
import io.github.luckybearbear.forge.demo.mold.api.RequestRepairRequest;
import io.github.luckybearbear.forge.demo.mold.app.MoldService;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * app 端模具接口：包名含 .controller.app. → platform-web 双端前缀映射自动前插 /app-api
 * （spec §6.5），实际路径 /app-api/molds、/app-api/molds/{id}、/app-api/molds/{id}/repairs。
 */
@RestController
@RequestMapping("/molds")
public class MoldController {

    private final MoldService moldService;

    public MoldController(MoldService moldService) {
        this.moldService = moldService;
    }

    @PostMapping
    public Result<MoldDTO> register(@Valid @RequestBody RegisterMoldRequest request) {
        return Result.ok(moldService.register(request));
    }

    @GetMapping("/{id}")
    public Result<MoldDTO> get(@PathVariable String id) {
        return Result.ok(moldService.findById(id));
    }

    @PostMapping("/{id}/repairs")
    public Result<Void> requestRepair(@PathVariable String id,
                                      @Valid @RequestBody RequestRepairRequest request) {
        moldService.requestRepair(id, request);
        return Result.ok();
    }
}
