package dev.ringworld.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Wire-compatible codecs retained where 1.21.1 lacks newer named constants. */
final class RingWireCodecs {
    static final StreamCodec<ByteBuf, Long> LONG = StreamCodec.of(
            (buffer, value) -> buffer.writeLong(value), ByteBuf::readLong);

    private RingWireCodecs() { }
}
