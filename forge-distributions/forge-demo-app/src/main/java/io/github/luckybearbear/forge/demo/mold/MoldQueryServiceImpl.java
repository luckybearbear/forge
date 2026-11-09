package io.github.luckybearbear.forge.demo.mold;

import io.github.luckybearbear.forge.contract.mold.MoldDTO;
import io.github.luckybearbear.forge.contract.mold.MoldQueryService;
import io.github.luckybearbear.forge.demo.mold.app.MoldService;
import org.springframework.stereotype.Component;

/** 契约实现：其他模块经此同步查询，禁止直接访问模块内部。 */
@Component
public class MoldQueryServiceImpl implements MoldQueryService {

    private final MoldService moldService;

    public MoldQueryServiceImpl(MoldService moldService) {
        this.moldService = moldService;
    }

    @Override
    public MoldDTO findById(String moldId) {
        return moldService.findById(moldId);
    }
}
