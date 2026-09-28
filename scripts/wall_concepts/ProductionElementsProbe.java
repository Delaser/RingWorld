import java.lang.instrument.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit integration probe for the disposable Industrial study copy only. */
public class ProductionElementsProbe {
 public static void agentmain(String output, Instrumentation inst) throws Exception {
  Class<?> mc=null; for(var c:inst.getAllLoadedClasses()) if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;
  ClassLoader cl=mc.getClassLoader(); Object client=mc.getMethod("getInstance").invoke(null),server=mc.getMethod("getSingleplayerServer").invoke(client);
  new Thread(()->{try {
   for(int cx=256;cx<336;cx++) { final int chunkX=cx; var done=new CompletableFuture<Void>();
    server.getClass().getMethod("execute",Runnable.class).invoke(server,(Runnable)()->{try {
     Class<?> style=Class.forName("dev.ringworld.world.RingWallStyle",true,cl),palette=Class.forName(style.getName()+"$Palette",true,cl),pattern=Class.forName(style.getName()+"$Pattern",true,cl);
     Object selected=style.getMethod("custom",int.class,palette,pattern,int.class).invoke(null,7,Enum.valueOf((Class)palette,"INDUSTRIAL"),Enum.valueOf((Class)pattern,"ENGINEERED"),10);
     Object level=server.getClass().getMethod("overworld").invoke(server),source=level.getClass().getMethod("getChunkSource").invoke(level),generator=source.getClass().getMethod("getGenerator").invoke(source),random=source.getClass().getMethod("randomState").invoke(source);
     Class<?> access=Class.forName("dev.ringworld.world.RingWorldGeneratorAccess",true,cl),geometry=Class.forName("dev.ringworld.world.RingGeometry",true,cl),chunk=Class.forName("net.minecraft.world.level.chunk.ChunkAccess",true,cl),boundary=Class.forName("dev.ringworld.world.RingGenerationBoundary",true,cl),placement=Class.forName("dev.ringworld.world.RingIndustrialElementPlacement",true,cl),terrain=Class.forName(placement.getName()+"$TerrainHeight",true,cl);
     Object geo=access.getMethod("ringworld$getGeometry").invoke(generator); long seed=(long)level.getClass().getMethod("getSeed").invoke(level);
     Class<?> types=Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types",true,cl); Object heightType=Enum.valueOf((Class)types,"WORLD_SURFACE_WG");
     Method height=Arrays.stream(generator.getClass().getMethods()).filter(m->m.getName().equals("getBaseHeight")&&m.getParameterCount()==5).findFirst().orElseThrow();
     int[] queries={0}; Object query=Proxy.newProxyInstance(cl,new Class[]{terrain},(p,m,a)->{queries[0]++;return height.invoke(generator,a[0],a[1],heightType,level,random);});
     Object target=level.getClass().getMethod("getChunk",int.class,int.class).invoke(level,chunkX,-8);
     Class<?> pos=Class.forName("net.minecraft.core.BlockPos",true,cl),state=Class.forName("net.minecraft.world.level.block.state.BlockState",true,cl); Constructor<?> bp=pos.getConstructor(int.class,int.class,int.class);
     Method get=chunk.getMethod("getBlockState",pos),notify=level.getClass().getMethod("sendBlockUpdated",pos,state,state,int.class);
     boundary.getMethod("installRim",chunk,geometry,int.class,style,long.class).invoke(null,target,geo,160,selected,seed);
     var positions=new ArrayList<Object>();var before=new ArrayList<Object>();
     for(int x=chunkX*16;x<chunkX*16+16;x++)for(int y=48;y<96;y++)for(int z=-125;z<=-119;z++){Object p=bp.newInstance(x,y,z);positions.add(p);before.add(get.invoke(target,p));}
     long start=System.nanoTime(); placement.getMethod("install",chunk,geometry,int.class,style,long.class,terrain).invoke(null,target,geo,160,selected,seed,query);double ms=(System.nanoTime()-start)/1e6;
     int changed=0;for(int i=0;i<positions.size();i++){Object after=get.invoke(target,positions.get(i));if(!after.equals(before.get(i))){changed++;notify.invoke(level,positions.get(i),before.get(i),after,3);}}
     Files.writeString(Path.of(output),"chunk="+chunkX+" changed="+changed+" queries="+queries[0]+" ms="+ms+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);done.complete(null);
    }catch(Throwable e){done.completeExceptionally(e);}});done.get(30,TimeUnit.SECONDS);Thread.sleep(30);
   }
   Files.writeString(Path.of(output),"PASS production installer completed\n",StandardOpenOption.APPEND);
  }catch(Throwable e){try{Files.writeString(Path.of(output),"FAIL "+e+" cause="+e.getCause()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}},"Production elements integration").start();
 }
 public static void main(String[] a)throws Exception{var vm=com.sun.tools.attach.VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
}
