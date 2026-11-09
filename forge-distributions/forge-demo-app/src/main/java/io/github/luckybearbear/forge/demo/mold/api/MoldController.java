package io.github.luckybearbear.forge.demo.mold.api;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.demo.mold.app.MoldService;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/molds")
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
