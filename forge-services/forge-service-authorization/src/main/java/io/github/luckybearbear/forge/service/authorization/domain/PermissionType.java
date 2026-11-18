package io.github.luckybearbear.forge.service.authorization.domain;

/**
 * 权限点类型（菜单合一模型，四值）：
 * <ul>
 *   <li>{@link #DIR}——目录（组菜单树，<b>不参与鉴权</b>：无 code 级权限语义，可见性由后代决定）</li>
 *   <li>{@link #MENU}——菜单（code 即权限码：鉴权与菜单可见性同源）</li>
 *   <li>{@link #BUTTON}——按钮（页面内操作点，code 参与鉴权；不进可见菜单树）</li>
 *   <li>{@link #API}——接口（@PreAuthorize hasAuthority 消费；不进可见菜单树）</li>
 * </ul>
 */
public enum PermissionType {
    DIR, MENU, BUTTON, API
}
