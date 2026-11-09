package io.github.luckybearbear.forge.platform.storage;

import java.io.InputStream;

/** 文件存储抽象 SPI。Phase 3 提供本地/对象存储实现与文件中心。 */
public interface StorageClient {

    /** 返回存储 key。 */
    String store(InputStream content, String filename, String contentType);

    InputStream read(String key);
}
