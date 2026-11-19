package dev.ringworld.server;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class SampleBench {
    private static volatile int sink;
    private static long sample(LevelChunk chunk, boolean layers) {
        var p=new BlockPos.MutableBlockPos();
        int sum=0;
        long t=System.nanoTime();
        for(int z=1;z<16;z+=2) for(int x=1;x<16;x+=2) {
            sum+=RingAtlasSurfaceSampler.surfaceY(chunk,x,z);
            if (!layers) continue;
            int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            p.set(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
            var state=chunk.getBlockState(p);
            if (!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)&&!state.is(Blocks.STONE_BRICKS)
                    &&!state.is(Blocks.POLISHED_ANDESITE)&&!state.is(Blocks.SPRUCE_PLANKS))continue;
            int air=0,best=0,bottom=0,upper=0;
            for(int n=1;n<=32 && y-n>=chunk.getMinY();n++){
                p.setY(y-n);
                var below=chunk.getBlockState(p);
                if(below.isAir()){air++;continue;}
                if(air>=2&&air>=best){best=air;bottom=y-n+1;upper=bottom+air;}
                air=0;
                if(!below.is(BlockTags.LOGS)&&!below.is(BlockTags.LEAVES)&&!below.is(Blocks.STONE_BRICKS)
                        &&!below.is(Blocks.POLISHED_ANDESITE)&&!below.is(Blocks.SPRUCE_PLANKS)) break;
            }
            sum+=bottom+upper;
        }
        sink=sum;
        return System.nanoTime()-t;
    }
    public static String measure(LevelChunk c,int i){
        boolean first=(i&1)==0;
        long a=sample(c,first);long b=sample(c,!first);long d=sample(c,!first);long e=sample(c,first);
        return i+","+(first?(b+d):(a+e))/2+","+(first?(a+e):(b+d))/2;
    }
}
