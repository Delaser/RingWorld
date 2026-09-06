import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.*;
public class LargeWallProbe {
 public static void agentmain(String path,Instrumentation inst)throws Exception {
  Class<?> mc=null;for(Class<?> c:inst.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;
  final ClassLoader cl=mc.getClassLoader();Object client=mc.getMethod("getInstance").invoke(null);Object server=mc.getMethod("getSingleplayerServer").invoke(client);
  Class<?> style=Class.forName("dev.ringworld.world.RingWallStyle",true,cl),palette=Class.forName("dev.ringworld.world.RingWallStyle$Palette",true,cl),pattern=Class.forName("dev.ringworld.world.RingWallStyle$Pattern",true,cl),boundary=Class.forName("dev.ringworld.world.RingGenerationBoundary",true,cl),sampler=Class.forName("dev.ringworld.world.RingWallPattern",true,cl),pos=Class.forName("net.minecraft.core.BlockPos",true,cl),state=Class.forName("net.minecraft.world.level.block.state.BlockState",true,cl),blocks=Class.forName("net.minecraft.world.level.block.Blocks",true,cl);
  Method sample=boundary.getDeclaredMethod("texturedRimBlock",style,int.class,int.class,int.class,int.class,long.class);sample.setAccessible(true);
  Method present=sampler.getMethod("blockPresent",style,int.class,int.class,int.class,int.class,int.class,long.class);
  Constructor<?> position=pos.getConstructor(int.class,int.class,int.class);
  Object airBlock=blocks.getField("AIR").get(null),air=airBlock.getClass().getMethod("defaultBlockState").invoke(airBlock);
  new Thread(()->{try{
   for(int index=0;index<1;index++){
    int decay=10;String name="ENGINEERED";
    Object selected=style.getMethod("custom",int.class,palette,pattern,int.class).invoke(null,7,Enum.valueOf((Class)palette,"INDUSTRIAL"),Enum.valueOf((Class)pattern,name),decay);
    final int origin=6144;
    int[][] rolls=new int[7][];
    Method roll=sampler.getMethod("materialRoll",style,int.class,int.class,int.class,int.class,long.class);
    Method paletteRoll=boundary.getMethod("styledRimBlockForRoll",style,int.class);
    for(int d=0;d<7;d++){
     int[] baseline=new int[384*96];
     for(int y=0;y<96;y++)for(int x=0;x<384;x++)baseline[y*384+x]=(Integer)roll.invoke(null,selected,6144+x,128+y,d,16384,8128L);
     long startNanos=System.nanoTime();rolls[d]=baseline;
     Files.writeString(Path.of(path),"SAMPLE "+index+" depth="+d+" ns="+(System.nanoTime()-startNanos)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    for(int begin=0;begin<384;begin+=4){final int start=begin;var done=new CompletableFuture<Void>();
     server.getClass().getMethod("execute",Runnable.class).invoke(server,(Runnable)()->{try{
      Object level=server.getClass().getMethod("overworld").invoke(server);Method set=level.getClass().getMethod("setBlock",pos,state,int.class);
      for(int x=start;x<start+4;x++)for(int d=0;d<7;d++)for(int y=128;y<224;y++){
       Object block=(Boolean)present.invoke(null,selected,6144+x,y,d,224,16384,8128L)?paletteRoll.invoke(null,selected,LargeWallPattern.roll(x,y-128,rolls[d][(y-128)*384+x])):air;
       if(LargeWallPattern.recess(x,y-128,d))block=air;
       if(LargeWallPattern.light(x,y-128,d)){
        Object lantern=blocks.getField("SEA_LANTERN").get(null);
        block=lantern.getClass().getMethod("defaultBlockState").invoke(lantern);
       }
       set.invoke(level,position.newInstance(origin+x,y,-84-d),block,2);
      }done.complete(null);
     }catch(Throwable e){done.completeExceptionally(e);}});done.get(30,TimeUnit.SECONDS);Thread.sleep(25);
    }
    Files.writeString(Path.of(path),"READY "+index+" "+"Large armour and gate"+" decay="+decay+" x="+origin+".."+(origin+383)+" y=128..223 z=-90..-84\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
  }catch(Exception e){e.printStackTrace();try{Files.writeString(Path.of(path),"FAIL "+e+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}},"Industrial wall study setup").start();
 }
 public static void main(String[] a)throws Exception{var vm=com.sun.tools.attach.VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
}
