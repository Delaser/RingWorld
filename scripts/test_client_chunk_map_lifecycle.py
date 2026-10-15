"""Compile the real client registry and exercise native weak-reference ownership.

Minecraft types are minimal compile stubs; the implementation under test is the
unchanged repository source. Storage intentionally points back to its owner,
matching vanilla's private chunk storage and its loaded chunk/level graph.
"""
import os,shutil,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
STUBS={
 'org/jetbrains/annotations/Nullable.java':'package org.jetbrains.annotations; public @interface Nullable {}',
 'net/minecraft/client/multiplayer/ClientChunkCache.java':'package net.minecraft.client.multiplayer; public final class ClientChunkCache { public Object storage; }',
 'dev/ringworld/client/chunk/ChunkMapOwnershipRegression.java':r'''
package dev.ringworld.client.chunk;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.client.multiplayer.ClientChunkCache;
public final class ChunkMapOwnershipRegression {
    private static final class Storage implements RingClientChunkMapAccess {
        final ClientChunkCache owner;
        Storage(ClientChunkCache owner) { this.owner=owner; }
        public int ringworld$centerChunkX(){return 7;}
        public int ringworld$centerChunkZ(){return -9;}
        public void ringworld$clearAllChunks(){}
    }
    private static WeakReference<?>[] registerAbandonedCycle() {
        ClientChunkCache owner=new ClientChunkCache();Storage map=new Storage(owner);owner.storage=map;
        RingClientChunkMaps.register(owner,map);
        return new WeakReference<?>[]{new WeakReference<>(owner),new WeakReference<>(map)};
    }
    private static void awaitCollected(WeakReference<?>... references)throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<deadline) {
            System.gc();boolean cleared=true;
            for(var reference:references)cleared &= reference.get()==null;
            if(cleared)return;
            Thread.sleep(20);
        }
        throw new AssertionError("Registry retained an abandoned owner/storage cycle after bounded full GC");
    }
    private static void liveOwner() throws Exception {
        ClientChunkCache owner=new ClientChunkCache();owner.storage=new Storage(owner);
        RingClientChunkMaps.register(owner,(Storage)owner.storage);
        for(int i=0;i<5;i++) {
            System.gc();
            if(RingClientChunkMaps.get(owner)!=owner.storage)throw new AssertionError("Live native storage association vanished");
        }
        Reference.reachabilityFence(owner);
    }
    private static WeakReference<Storage> replace(ClientChunkCache owner) {
        Storage previous=new Storage(owner);owner.storage=previous;RingClientChunkMaps.register(owner,previous);
        WeakReference<Storage> weak=new WeakReference<>(previous);
        owner.storage=new Storage(owner);RingClientChunkMaps.register(owner,(Storage)owner.storage);
        return weak;
    }
    public static void main(String[] arguments)throws Exception {
        switch(arguments[0]) {
            case "abandoned" -> awaitCollected(registerAbandonedCycle());
            case "live" -> liveOwner();
            case "replacement" -> {
                ClientChunkCache owner=new ClientChunkCache();WeakReference<Storage> previous=replace(owner);
                awaitCollected(previous);
                if(RingClientChunkMaps.get(owner)!=owner.storage)throw new AssertionError("Replacement lost current map");
                Reference.reachabilityFence(owner);
            }
            default -> throw new AssertionError("Unknown ownership scenario");
        }
    }
}
'''}
class ClientChunkMapLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.addClassCleanup(cls.temp.cleanup);cls.build=Path(cls.temp.name)
        suffix='.exe' if os.name=='nt' else ''
        java_home=Path(os.environ.get('JAVA_HOME',''))
        cls.java=str(java_home/'bin'/('java'+suffix));javac=str(java_home/'bin'/('javac'+suffix))
        if not Path(javac).is_file():javac,cls.java=shutil.which('javac'),shutil.which('java')
        if not javac or not cls.java:raise AssertionError('JDK17 or newer required for client ownership regression')
        for name,source in STUBS.items():
            path=cls.build/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source,encoding='utf-8')
        source=ROOT/'src/client/java/dev/ringworld/client/chunk/RingClientChunkMaps.java'
        interface=ROOT/'src/client/java/dev/ringworld/client/chunk/RingClientChunkMapAccess.java'
        result=subprocess.run([javac,'--release','17','-d',str(cls.build),*map(str,cls.build.rglob('*.java')),str(source),str(interface)],capture_output=True,text=True,timeout=60)
        if result.returncode:raise AssertionError(result.stdout+result.stderr)
    def run_scenario(self,name):
        result=subprocess.run([self.java,'-Xmx64M','-cp',str(self.build),'dev.ringworld.client.chunk.ChunkMapOwnershipRegression',name],capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
    def test_abandoned_owner_and_storage_are_collectible(self):self.run_scenario('abandoned')
    def test_live_owner_keeps_current_storage(self):self.run_scenario('live')
    def test_replaced_storage_can_collect(self):self.run_scenario('replacement')
if __name__=='__main__':unittest.main()
