package io.github.luckybearbear.forge.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** 请求体可重复读（访问日志/审计前置）。排除 URL 不包装。 */
public class CacheRequestBodyFilter extends OncePerRequestFilter {

    private final long maxBodySize;
    private final Set<String> excludeUrls;

    public CacheRequestBodyFilter(long maxBodySize, Set<String> excludeUrls) {
        this.maxBodySize = maxBodySize;
        this.excludeUrls = excludeUrls;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludeUrls.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request, (int) maxBodySize);
        filterChain.doFilter(wrapper, response);
    }
}
