package io.github.luckybearbear.forge.service.authorization.domain;

import io.github.luckybearbear.forge.platform.data.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 权限点实体——<b>全局表</b>（extends AbstractAuditableEntity，无 @TenantId）：权限字典是平台级
 * 领域语言（{@code user:manage}/{@code mold:read}...），所有租户共享同一字典，租户侧只经
 * auth_role_permission 做"角色-权限点"引用。code 全局唯一（partial unique index WHERE deleted = FALSE）。
 *
 * <p><b>菜单合一模型（Task 1 菜单切片）</b>：菜单=带路由元数据的权限点——本表直接承载菜单树
 * （不建新表/不加绑定表）：parentId 组树、path/component/icon/sort/visible 为前端路由元数据；
 * type 四值 DIR/MENU/BUTTON/API。MENU 的 code 即权限码（鉴权与菜单可见性同源）；DIR 不参与
 * 鉴权（可见性由后代决定）。六列均可空（DIR 无路由字段；visible 列 NOT NULL DEFAULT TRUE，
 * 代码侧 create 缺省 TRUE 对齐）。
 */
@Entity
@Table(name = "auth_permission")
public class PermissionEntity extends AbstractAuditableEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 权限点类型：DIR（目录，不参与鉴权）/ MENU（菜单，code 即权限码）/ BUTTON / API。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PermissionType type;

    /** 父节点（目录组树）；null/空白 = 根。逻辑引用（无物理 FK），环由可见性算法防御。 */
    @Column(name = "parent_id", length = 36)
    private String parentId;

    /** 前端路由 path（MENU 型路由字段；DIR/BUTTON/API 可空）。 */
    @Column(length = 256)
    private String path;

    /** 前端组件串（约定根 views/，如 system/tenant/index）。 */
    @Column(length = 256)
    private String component;

    /** 菜单图标名（前端图标组件映射）。 */
    @Column(length = 64)
    private String icon;

    /** 同级排序（升序，null 最后）。 */
    @Column(name = "sort")
    private Integer sort;

    /** 前端展示开关（隐藏但路由可注册）——后端可见性不据此过滤。列 NOT NULL DEFAULT TRUE。 */
    @Column(nullable = false)
    private Boolean visible;

    protected PermissionEntity() {} // JPA

    public PermissionEntity(String code, String name, PermissionType type) {
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PermissionType getType() { return type; }
    public void setType(PermissionType type) { this.type = type; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
}
