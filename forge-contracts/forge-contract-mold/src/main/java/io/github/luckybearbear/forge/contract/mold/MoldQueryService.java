package io.github.luckybearbear.forge.contract.mold;

/** 跨模块同步查询契约：mold 模块实现，其他模块消费。 */
public interface MoldQueryService {

    MoldDTO findById(String moldId);
}
