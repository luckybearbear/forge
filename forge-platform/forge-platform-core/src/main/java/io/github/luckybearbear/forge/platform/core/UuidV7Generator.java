package io.github.luckybearbear.forge.platform.core;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID v7：48-bit 毫秒时间戳 + 随机位，时间有序利于索引。
 */
public class UuidV7Generator implements IdGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String nextId() {
        long ts = System.currentTimeMillis();
        byte[] r = new byte[10];
        random.nextBytes(r);
        byte[] b = new byte[16];
        for (int i = 0; i < 6; i++) {
            b[i] = (byte) ((ts >> ((5 - i) * 8)) & 0xFF);
        }
        b[6] = (byte) (0x70 | (r[0] & 0x0F));
        b[7] = r[1];
        b[8] = (byte) (0x80 | (r[2] & 0x3F));
        System.arraycopy(r, 3, b, 9, 7);
        ByteBuffer buffer = ByteBuffer.wrap(b);
        return new UUID(buffer.getLong(), buffer.getLong()).toString();
    }
}
