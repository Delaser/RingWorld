"""Execute the actual final-stop cleanup method with minimal ownership stubs.

The full class is compiled by each loader build. This bounded harness extracts
the real method so no second implementation can quietly replace the cleanup.
Native dual-loader shutdown/reopen checks still prove the event plumbing.
"""
import os,shutil,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def cleanup_method(source):
    marker='public static void onServerStopped(MinecraftServer server)'
    start=source.index(marker);opening=source.index('{',start);depth=0
    for end in range(opening,len(source)):
        if source[end]=='{':depth+=1
        elif source[end]=='}':
            depth-=1
            if depth==0:return source[start:end+1]
    raise AssertionError('Final-stop cleanup method is incomplete')

STUBS={
 'net/minecraft/server/MinecraftServer.java':'''package net.minecraft.server;
 public final class MinecraftServer { public final java.util.List<Object> worlds=new java.util.ArrayList<>(); }''',
 'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level;
 public final class ServerLevel { private final net.minecraft.server.MinecraftServer server;
 public ServerLevel(net.minecraft.server.MinecraftServer server){this.server=server;server.worlds.add(this);}
 public net.minecraft.server.MinecraftServer getServer(){return server;} }''',
}
HARNESS=r'''
package dev.ringworld.server;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
public final class ServerCacheOwnershipRegression {
    private static final Map<ServerLevel,Object> WORLD_GEOMETRY=new IdentityHashMap<>();
    private static final Map<ServerLevel,Object> PENDING_LEGACY_RIM_MIGRATIONS=new IdentityHashMap<>();
    /* ACTUAL_CLEANUP_METHOD */
    private static WeakReference<?>[] closeOwnedGraph() {
        var server=new MinecraftServer();var world=new ServerLevel(server);
        WORLD_GEOMETRY.put(world,new Object());PENDING_LEGACY_RIM_MIGRATIONS.put(world,world);
        onServerStopped(server);
        return new WeakReference<?>[]{new WeakReference<>(server),new WeakReference<>(world)};
    }
    private static void awaitCollected(WeakReference<?>... values)throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<deadline) {
            System.gc();boolean cleared=true;for(var value:values)cleared &= value.get()==null;
            if(cleared)return;Thread.sleep(20);
        }
        throw new AssertionError("Stopped world/server graph remains owned by static caches");
    }
    public static void main(String[] args)throws Exception {
        if(args[0].equals("collect")){awaitCollected(closeOwnedGraph());return;}
        var closed=new MinecraftServer();var world=new ServerLevel(closed);var nether=new ServerLevel(closed);
        var live=new MinecraftServer();var other=new ServerLevel(live);Object value=new Object();
        WORLD_GEOMETRY.put(world,value);WORLD_GEOMETRY.put(nether,value);WORLD_GEOMETRY.put(other,value);
        PENDING_LEGACY_RIM_MIGRATIONS.put(world,world);PENDING_LEGACY_RIM_MIGRATIONS.put(other,other);
        if(args[0].equals("late")) {
            WORLD_GEOMETRY.remove(world); // Earlier level-unload callback.
            WORLD_GEOMETRY.put(world,value); // Final closing work consults geometry again.
        }
        onServerStopped(closed);
        if(args[0].equals("idempotent"))onServerStopped(closed);
        if(WORLD_GEOMETRY.size()!=1||WORLD_GEOMETRY.get(other)!=value
            ||PENDING_LEGACY_RIM_MIGRATIONS.size()!=1||PENDING_LEGACY_RIM_MIGRATIONS.get(other)!=other)
            throw new AssertionError("Final cleanup missed an owned level or removed another server");
    }
}
'''
class ServerWorldCacheLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.addClassCleanup(cls.temp.cleanup);cls.build=Path(cls.temp.name)
        suffix='.exe' if os.name=='nt' else '';java_home=Path(os.environ.get('JAVA_HOME',''))
        javac=str(java_home/'bin'/('javac'+suffix));cls.java=str(java_home/'bin'/('java'+suffix))
        if not Path(javac).is_file():javac,cls.java=shutil.which('javac'),shutil.which('java')
        if not javac or not cls.java:raise AssertionError('JDK17 or newer required for server ownership regression')
        source=(ROOT/'src/main/java/dev/ringworld/server/RingWorldServer.java').read_text(encoding='utf-8')
        files=STUBS|{'dev/ringworld/server/ServerCacheOwnershipRegression.java':HARNESS.replace('/* ACTUAL_CLEANUP_METHOD */',cleanup_method(source))}
        for name,text in files.items():
            path=cls.build/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8')
        result=subprocess.run([javac,'--release','17','-d',str(cls.build),*map(str,cls.build.rglob('*.java'))],capture_output=True,text=True,timeout=60)
        if result.returncode:raise AssertionError(result.stdout+result.stderr)
    def scenario(self,name):
        result=subprocess.run([self.java,'-Xmx64M','-cp',str(self.build),'dev.ringworld.server.ServerCacheOwnershipRegression',name],capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
    def test_owned_server_and_world_graph_can_collect(self):self.scenario('collect')
    def test_final_cleanup_removes_late_repopulation(self):self.scenario('late')
    def test_other_servers_remain_owned(self):self.scenario('other')
    def test_final_cleanup_is_idempotent(self):self.scenario('idempotent')
if __name__=='__main__':unittest.main()
