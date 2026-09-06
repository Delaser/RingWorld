import java.lang.instrument.*;import java.lang.reflect.*;import java.nio.file.*;
/** Bounded installer check: one existing study-world chunk, all upper layers unchanged. */
public class UndersideProbe {
 public static void agentmain(String out,Instrumentation inst)throws Exception {
  Class<?> mc=null;for(var c:inst.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;
  ClassLoader cl=mc.getClassLoader();Object client=mc.getMethod("getInstance").invoke(null),server=mc.getMethod("getSingleplayerServer").invoke(client);
  server.getClass().getMethod("execute",Runnable.class).invoke(server,(Runnable)()->{try{
   Object level=server.getClass().getMethod("overworld").invoke(server),source=level.getClass().getMethod("getChunkSource").invoke(level),generator=source.getClass().getMethod("getGenerator").invoke(source);
   Class<?> access=Class.forName("dev.ringworld.world.RingWorldGeneratorAccess",true,cl),geo=Class.forName("dev.ringworld.world.RingGeometry",true,cl),style=Class.forName("dev.ringworld.world.RingWallStyle",true,cl),ca=Class.forName("net.minecraft.world.level.chunk.ChunkAccess",true,cl),bp=Class.forName("net.minecraft.core.BlockPos",true,cl);
   Object geometry=access.getMethod("ringworld$getGeometry").invoke(generator),selected=access.getMethod("ringworld$getWallStyle").invoke(generator),chunk=level.getClass().getMethod("getChunk",int.class,int.class).invoke(level,22,0);
   int bottom=(int)ca.getMethod("getMinY").invoke(chunk);Method get=ca.getMethod("getBlockState",bp);Constructor<?> pos=bp.getConstructor(int.class,int.class,int.class);
   Object[] positions=new Object[256*6],before=new Object[positions.length];int n=0;
   for(int y=bottom;y<bottom+6;y++)for(int z=0;z<16;z++)for(int x=352;x<368;x++){positions[n]=pos.newInstance(x,y,z);before[n]=get.invoke(chunk,positions[n]);n++;}
   Class<?> boundary=Class.forName("dev.ringworld.world.RingGenerationBoundary",true,cl);Method install=boundary.getMethod("installUnderside",ca,geo,style,long.class);
   long seed=(long)level.getClass().getMethod("getSeed").invoke(level);install.invoke(null,chunk,geometry,selected,seed);
   int changed=0;Object[] after=new Object[n];
   for(int j=0;j<n;j++){after[j]=get.invoke(chunk,positions[j]);if(!after[j].equals(before[j])){if(j>=256||!before[j].toString().contains("bedrock"))throw new AssertionError("unexpected layer/material change "+j);changed++;}}
   install.invoke(null,chunk,geometry,selected,seed);
   for(int j=0;j<n;j++)if(!after[j].equals(get.invoke(chunk,positions[j])))throw new AssertionError("not idempotent");
   if(changed==0)throw new AssertionError("no bedrock converted");
   Files.writeString(Path.of(out),"PASS bottom="+bottom+" converted="+changed+" upper1280=unchanged idempotent=true chunk=22,0\n");
  }catch(Throwable e){try{Files.writeString(Path.of(out),"FAIL "+e+" cause="+e.getCause());}catch(Exception ignored){}}});
 }
 public static void main(String[] a)throws Exception{var v=com.sun.tools.attach.VirtualMachine.attach(a[0]);try{v.loadAgent(a[1],a[2]);}finally{v.detach();}}
}
