package io.github.luckybearbear.forge.service.organization.app;

import io.github.luckybearbear.forge.contract.organization.OrgTreeQueryService;
import io.github.luckybearbear.forge.contract.organization.OrgUnitDTO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 契约 {@link OrgTreeQueryService} 实现：跨模块同步查询入口（授权数据域按部门子树收敛等）。
 *
 * <p><b>租户视角契约</b>：org_unit 为租户表——@TenantId 判别式过滤总生效，本查询只在调用方
 * 租户上下文内命中（web 请求链自动；异步侧显式 set/finally clear）；根不在本租户/已删 →
 * 空快照（消费方据此失效缓存，不区分 404——快照语义）。
 */
@Service
public class OrgTreeQueryServiceImpl implements OrgTreeQueryService {

    private final OrgUnitService orgUnitService;

    public OrgTreeQueryServiceImpl(OrgUnitService orgUnitService) {
        this.orgUnitService = orgUnitService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrgUnitDTO> findSubTree(String rootOrgUnitId) {
        return orgUnitService.subTree(rootOrgUnitId);
    }
}
