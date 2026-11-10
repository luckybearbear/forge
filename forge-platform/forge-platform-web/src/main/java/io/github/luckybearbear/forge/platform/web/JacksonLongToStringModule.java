package io.github.luckybearbear.forge.platform.web;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Long → String 序列化（防 JS 精度丢失，spec §6.5）。
 * Jackson 3（tools.jackson）：serialize 第三参为 {@link SerializationContext}，
 * 受检异常已移除（JacksonException 为 unchecked），故无 throws 声明。
 */
public class JacksonLongToStringModule extends SimpleModule {

    public JacksonLongToStringModule() {
        super("forge-long-to-string");
        addSerializer(Long.class, new LongToStringSerializer(Long.class));
        addSerializer(Long.TYPE, new LongToStringSerializer(Long.TYPE));
    }

    static final class LongToStringSerializer extends StdSerializer<Long> {
        LongToStringSerializer(Class<?> type) { super(type, false); }
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializationContext provider) {
            gen.writeString(String.valueOf(value));
        }
    }
}
