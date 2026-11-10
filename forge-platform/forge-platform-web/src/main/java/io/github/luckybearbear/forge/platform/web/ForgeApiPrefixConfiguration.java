package io.github.luckybearbear.forge.platform.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 双端前缀（spec §6.5）：controller.admin.* → /admin-api，controller.app.* → /app-api。
 *
 * <p>通过 Boot 4 的 {@link WebMvcRegistrations}（Boot 4 已迁移至
 * {@code org.springframework.boot.webmvc.autoconfigure} 包）替换默认 RequestMappingHandlerMapping。
 * 前缀属性由 starter 绑定 {@code forge.web.api-prefix.*} 后以 {@link ForgeApiPrefixProperties}
 * bean 注入；无 bean 时回退 {@link ForgeApiPrefixProperties#defaults()}。
 */
@Configuration
public class ForgeApiPrefixConfiguration {

    @Bean
    public WebMvcRegistrations forgeApiPrefixWebMvcRegistrations(
            ObjectProvider<ForgeApiPrefixProperties> properties) {
        ForgeApiPrefixProperties resolved = properties.getIfAvailable(ForgeApiPrefixProperties::defaults);
        // Boot 4 的 WebMvcRegistrations 三个方法皆为 default，不再是函数接口，无法用 lambda。
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new ForgeApiPrefixRequestMappingHandlerMapping(resolved);
            }
        };
    }
}
