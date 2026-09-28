import java.lang.instrument.*;import java.lang.reflect.*;import java.nio.file.*;import java.util.concurrent.*;
public class ScaledWallProbe2 {
 public static void agentmain(String path,Instrumentation inst)throws Exception{
  Class<?> mc=null;for(Class<?> c:inst.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;
  ClassLoader cl=mc.getClassLoader();Object client=mc.getMethod("getInstance").invoke(null),server=mc.getMethod("getSingleplayerServer").invoke(client);
  Class<?> style=Class.forName("dev.ringworld.world.RingWallStyle",true,cl),palette=Class.forName("dev.ringworld.world.RingWallStyle$Palette",true,cl),pattern=Class.forName("dev.ringworld.world.RingWallStyle$Pattern",true,cl),boundary=Class.forName("dev.ringworld.world.RingGenerationBoundary",true,cl),pos=Class.forName("net.minecraft.core.BlockPos",true,cl),state=Class.forName("net.minecraft.world.level.block.state.BlockState",true,cl),blocks=Class.forName("net.minecraft.world.level.block.Blocks",true,cl);
  Object selected=style.getMethod("custom",int.class,palette,pattern,int.class).invoke(null,7,Enum.valueOf((Class)palette,"INDUSTRIAL"),Enum.valueOf((Class)pattern,"PANELS"),10);
  Method material=boundary.getMethod("styledRimBlockForRoll",style,int.class);Constructor<?> position=pos.getConstructor(int.class,int.class,int.class);
  Object airBlock=blocks.getField("AIR").get(null),air=airBlock.getClass().getMethod("defaultBlockState").invoke(airBlock);
  new Thread(()->{try{
   for(int k=0;k<8;k++){final int kind=k;
    for(int start=8;start<56;start+=4){final int begin=start;var done=new CompletableFuture<Void>();
     server.getClass().getMethod("execute",Runnable.class).invoke(server,(Runnable)()->{try{
      Object level=server.getClass().getMethod("overworld").invoke(server);Method set=level.getClass().getMethod("setBlock",pos,state,int.class);
      for(int x=begin;x<begin+4;x++)for(int y=0;y<33;y++)for(int z=-3;z<=3;z++){
       int roll=ScaledWallElements2.roll(kind,x,y,z);if(roll==-999)continue;
       Object block=roll<0?air:material.invoke(null,selected,roll);
       set.invoke(level,position.newInstance(ScaledWallElements2.ORIGINS[kind]+x,63+y,-122+z),block,2);
      }done.complete(null);
     }catch(Throwable e){done.completeExceptionally(e);}});done.get(30,TimeUnit.SECONDS);Thread.sleep(25);
    }
    Files.writeString(Path.of(path),"READY "+k+" "+ScaledWallElements2.NAMES[k]+" origin="+ScaledWallElements2.ORIGINS[k]+" y=63..95 faceZ=-122\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
  }catch(Exception e){try{Files.writeString(Path.of(path),"FAIL "+e,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}}},"Scaled wall elements study").start();
 }
 public static void main(String[] a)throws Exception{var vm=com.sun.tools.attach.VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
}
