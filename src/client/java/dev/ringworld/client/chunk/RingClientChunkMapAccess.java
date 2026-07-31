package dev.ringworld.client.chunk;

/** Operations added to vanilla's private ClientChunkMap implementation. */
public interface RingClientChunkMapAccess {
    int ringworld$centerChunkX();
    int ringworld$centerChunkZ();
    void ringworld$clearAllChunks();
}
