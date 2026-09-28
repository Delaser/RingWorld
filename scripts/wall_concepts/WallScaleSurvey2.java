import java.lang.instrument.*;import java.nio.file.*;import java.lang.reflect.*;
public class WallScaleSurvey2 {
 public static void agentmain(String path,Instrumentation inst)throws Exception{
  Class<?> mc=null;for(Class<?> c:inst.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;
  var cl=mc.getClassLoader();Object client=mc.getMethod("getInstance").invoke(null),server=mc.getMethod("getSingleplayerServer").invoke(client);
  Class<?> state=Class.forName("dev.ringworld.client.ClientRingState",true,cl);
  int wall=(Integer)state.getMethod("wallHeightBlocks").invoke(null);
  Class<?> hm=Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types",true,cl);Object type=Enum.valueOf((Class)hm,"WORLD_SURFACE");
  server.getClass().getMethod("execute",Runnable.class).invoke(server,(Runnable)()->{try{
   Object level=server.getClass().getMethod("overworld").invoke(server);int min=(Integer)level.getClass().getMethod("getMinY").invoke(level);
   StringBuilder s=new StringBuilder("minY="+min+" wallHeight="+wall+" topExclusive="+(min+wall)+"\n");
   Method height=level.getClass().getMethod("getHeight",hm,int.class,int.class);
   for(int x=4096;x<5120;x+=64){for(int dx:new int[]{0,32,63})level.getClass().getMethod("getChunk",int.class,int.class).invoke(level,(x+dx)>>4,-8);s.append(x).append(',').append(height.invoke(level,type,x,-118)).append(',').append(height.invoke(level,type,x+32,-118)).append(',').append(height.invoke(level,type,x+63,-118)).append('\n');}
   Files.writeString(Path.of(path),s);
  }catch(Exception e){try{Files.writeString(Path.of(path),"FAIL "+e);}catch(Exception ignored){}}});
 }
 public static void main(String[] a)throws Exception{var vm=com.sun.tools.attach.VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
}
