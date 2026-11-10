package io.github.luckybearbear.forge.platform.web;

import java.lang.reflect.Method;

import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 双端前缀映射（spec §6.5）：handler 包名含 {@code .controller.admin.} → admin 前缀，
 * 含 {@code .controller.app.} → app 前缀，其余不加前缀；{@code enabled=false} 时全部不加。
 *
 * <p>钩子选择（javap 实证 spring-webmvc 7.0.10）：Spring Framework 7 无
 * {@code getPrefixFor(Class)} 钩子（brief 选项 a 不存在），故覆写
 * {@link #getMappingForMethod(Method, Class)}（brief 选项 b），用 Spring 7 新增的公开
 * {@link #getBuilderConfiguration()} 构造前缀 {@link RequestMappingInfo} 后
 * {@link RequestMappingInfo#combine(RequestMappingInfo)} 前插，保证 PatternParser 设置一致。
 */
public class ForgeApiPrefixRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private static final String APP_PACKAGE = ".controller.app.";
    private static final String ADMIN_PACKAGE = ".controller.admin.";

    private final ForgeApiPrefixProperties properties;

    public ForgeApiPrefixRequestMappingHandlerMapping(ForgeApiPrefixProperties properties) {
        this.properties = properties;
    }

    @Override
    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        RequestMappingInfo info = super.getMappingForMethod(method, handlerType);
        if (info == null) {
            return null;
        }
        String prefix = prefixFor(handlerType);
        if (prefix == null || prefix.isBlank()) {
            return info;
        }
        RequestMappingInfo prefixInfo = RequestMappingInfo.paths(prefix)
                .options(getBuilderConfiguration())
                .build();
        return prefixInfo.combine(info);
    }

    private String prefixFor(Class<?> handlerType) {
        if (!properties.enabled()) {
            return null;
        }
        // 包首尾补点后再 contains：同时匹配 controller.app 顶层包（无尾点）与 controller.app.v1 等子包。
        String padded = "." + handlerType.getPackageName() + ".";
        if (padded.contains(ADMIN_PACKAGE)) {
            return properties.adminPrefix();
        }
        if (padded.contains(APP_PACKAGE)) {
            return properties.appPrefix();
        }
        return null;
    }
}
