package io.github.luckybearbear.forge.platform.core;

/**
 * 分页查询参数。page 1 起始；size 钳制在 [1, 200]，默认 20。
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 200;
    public static final int DEFAULT_SIZE = 20;

    public PageQuery {
        if (page < 1) { page = 1; }
        if (size < 1) { size = DEFAULT_SIZE; }
        if (size > MAX_SIZE) { size = MAX_SIZE; }
    }

    public int offset() {
        return (page - 1) * size;
    }
}
