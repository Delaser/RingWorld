package local.rwprobe;
import dev.ringworld.client.*;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingLodQuality;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
public final class Study {
 public static volatile boolean layers;
 static int stage,pass,frames;static long last,warm;
 static StringBuilder csv=new StringBuilder("site,pass,layers,frame,ms\n");
 static void send(String s)throws Exception{Files.writeString(Path.of("../server.cmd"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static int x(){return pass<4?2200:7080;}
 static void begin()throws Exception{
  // A-B-B-A in the jungle; B-A-A-B in the spruce forest.
  layers=(pass==1||pass==2||pass==4||pass==7);
  if(pass==0||pass==4)send("tp RingJoinTester "+x()+" "+(pass<4?115:108)+" "+(pass<4?8:0)+" 90 12");
  RingSurfaceTextureRenderer.clear();
  System.out.println("Matched frame pass "+pass+" layers="+layers);
  warm=last=0;frames=0;stage=1;
 }
 public static void run()throws Exception {
  var c=Minecraft.getInstance();
  if(c.player==null||ClientRingState.terrainAtlas()==null||!ClientRingState.terrainAtlas().isComplete())return;
  if(stage==0){c.options.renderDistance().set(12);c.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
    c.options.enableVsync().set(false);c.options.pauseOnLostFocus=false;c.options.framerateLimit().set(260);
    RingMinecraftClientAccess.setGuiHidden(c,true);RingClientLodTuning.select(RingLodQuality.MEDIUM);
    send("gamemode spectator RingJoinTester");begin();return;}
  long now=System.nanoTime();
  if(stage==1){if(Math.abs(c.player.getX()-(x()+.5))>.01||!RingSurfaceTextureRenderer.displayReady()||!c.levelRenderer.hasRenderedAllSections()){warm=0;return;}
   if(warm==0)warm=now;if(now-warm<2_000_000_000L)return;last=now;stage=2;return;}
  if(stage==2){
    csv.append(pass<4?"jungle":"spruce").append(',').append(pass).append(',').append(layers?1:0)
      .append(',').append(frames).append(',').append((now-last)*1e-6).append('\n');last=now;
    if(++frames<200)return;
    if(++pass==8){Files.writeString(Path.of("abba.csv"),csv);stage=3;c.stop();return;}begin();
  }
 }
}
