package io.github.luckybearbear.forge.platform.core;

import java.util.List;

/**
 * 分页结果。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(PageQuery query, long total, List<T> items) {
        return new PageResult<>(items, total, query.page(), query.size());
    }
}
