"""Execute the real test coordinator against small Minecraft boundary doubles.

No game bootstrap or GL is needed: the regression replaces the player object
after transfer completion, exactly as integrated-server close/reopen does.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'dev/ringworld/RingWorldMod.java': '''package dev.ringworld;
public class RingWorldMod { public static final Log LOGGER=new Log();
public static class Log { public void info(String s,Object... a){} public void error(String s,Object... a){} }}''',
    'dev/ringworld/api/RingWorldApi.java': '''package dev.ringworld.api;
public class RingWorldApi { public static boolean isRingWorld(net.minecraft.server.level.ServerLevel l){return true;}
public static dev.ringworld.world.RingWorldSettings geometry(net.minecraft.server.level.ServerLevel l){return new dev.ringworld.world.RingWorldSettings();}}''',
    'dev/ringworld/world/RingWorldSettings.java': '''package dev.ringworld.world;
public class RingWorldSettings { public static final int DEFAULT_WIDTH=256,DEFAULT_CIRCUMFERENCE=16384;
public static RingWorldSettings get(net.minecraft.server.level.ServerLevel l){return new RingWorldSettings();}
public int widthBlocks(){return DEFAULT_WIDTH;} public int circumferenceBlocks(){return DEFAULT_CIRCUMFERENCE;}}''',
    'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;
public class Level { public static final net.minecraft.resources.ResourceKey<Level> OVERWORLD=new net.minecraft.resources.ResourceKey<>(), NETHER=new net.minecraft.resources.ResourceKey<>(), END=new net.minecraft.resources.ResourceKey<>(); }''',
    'net/minecraft/resources/ResourceKey.java': '''package net.minecraft.resources;
public class ResourceKey<T> { public String identifier(){return "test";} }''',
    'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;
public class ServerLevel { public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension(){return net.minecraft.world.level.Level.OVERWORLD;} }''',
    'net/minecraft/world/entity/Relative.java': 'package net.minecraft.world.entity; public enum Relative { X }',
    'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys;
public record Vec3(double x,double y,double z){public static final Vec3 ZERO=new Vec3(0,0,0);}''',
    'net/minecraft/world/level/portal/TeleportTransition.java': '''package net.minecraft.world.level.portal;
public class TeleportTransition { public static final Object DO_NOTHING=new Object();
public TeleportTransition(net.minecraft.server.level.ServerLevel l,net.minecraft.world.phys.Vec3 p,net.minecraft.world.phys.Vec3 v,float a,float b,java.util.Set<net.minecraft.world.entity.Relative> r,Object o){} }''',
    'net/minecraft/server/MinecraftServer.java': '''package net.minecraft.server;
public class MinecraftServer { public final java.util.List<net.minecraft.server.level.ServerPlayer> players=new java.util.ArrayList<>();
public MinecraftServer getPlayerList(){return this;} public java.util.List<net.minecraft.server.level.ServerPlayer> getPlayers(){return players;}
public net.minecraft.server.level.ServerLevel getLevel(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> k){return new net.minecraft.server.level.ServerLevel();} }''',
    'net/minecraft/server/level/ServerPlayer.java': '''package net.minecraft.server.level;
public class ServerPlayer { public final java.util.UUID id; public boolean alive=true,noGravity; public int updates;
public net.minecraft.world.phys.Vec3 velocity=new net.minecraft.world.phys.Vec3(0,-1,0);
public static class Abilities {public boolean mayfly,flying,invulnerable;} public final Abilities abilities=new Abilities();
public ServerPlayer(java.util.UUID id){this.id=id;} public java.util.UUID getUUID(){return id;} public boolean isAlive(){return alive;}
public Abilities getAbilities(){return abilities;} public void onUpdateAbilities(){updates++;} public void setNoGravity(boolean b){noGravity=b;}
public void setDeltaMovement(net.minecraft.world.phys.Vec3 v){velocity=v;} public ServerLevel level(){return new ServerLevel();}
public double getX(){return .5;} public float getYRot(){return 0;} public float getXRot(){return 0;}
public void teleport(net.minecraft.world.level.portal.TeleportTransition t){} }''',
    'SafetyRegression.java': '''import dev.ringworld.server.RingWorldProductionLifecycleTest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
public class SafetyRegression {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static boolean safe(ServerPlayer p){return p.noGravity && p.abilities.flying && p.abilities.mayfly && p.abilities.invulnerable && p.velocity.y()==0;}
public static void main(String[] args)throws Exception{
var server=new MinecraftServer(); var id=java.util.UUID.randomUUID();
var original=new ServerPlayer(id); server.players.add(original);
RingWorldProductionLifecycleTest.markPlayerForSafety(id);
RingWorldProductionLifecycleTest.tick(server);
check(!safe(original),"ordinary gameplay changed while opt-in disabled");
System.setProperty("ringworld.productionLifecycleTest","true");
RingWorldProductionLifecycleTest.tick(server); check(safe(original),"registered initial player unprotected");
for(int i=0;i<2400;i++)RingWorldProductionLifecycleTest.tick(server);
check(!RingWorldProductionLifecycleTest.transferCyclePassed()&&!RingWorldProductionLifecycleTest.transferCycleFailed(),"safety registration started transfers before complete Atlas baseline");
check(original.updates==1,"warmup sent redundant abilities packets");
RingWorldProductionLifecycleTest.markClientReady(id);
var field=RingWorldProductionLifecycleTest.class.getDeclaredField("transferCycleFinished"); field.setAccessible(true); field.setBoolean(null,true);
server.players.clear(); var reopened=new ServerPlayer(id); var unrelated=new ServerPlayer(java.util.UUID.randomUUID());
server.players.add(reopened); server.players.add(unrelated);
RingWorldProductionLifecycleTest.tick(server);
check(safe(reopened),"protection stopped after transfer completion / player replacement");
check(!safe(unrelated),"unregistered player changed");
RingWorldProductionLifecycleTest.tick(server); check(reopened.updates==1,"redundant abilities packets");
server.players.clear(); var dead=new ServerPlayer(id); dead.alive=false; server.players.add(dead);
RingWorldProductionLifecycleTest.tick(server); check(!safe(dead) && !dead.alive,"dead player altered or resurrected");
System.clearProperty("ringworld.productionLifecycleTest"); server.players.clear(); var disabled=new ServerPlayer(id); server.players.add(disabled);
RingWorldProductionLifecycleTest.tick(server); check(!safe(disabled),"disabled harness still changes player");
}}''',
}


class LifecyclePlayerSafetyTest(unittest.TestCase):
    def test_client_registers_safety_before_complete_baseline_without_marking_ready(self):
        client = (ROOT / 'src/client/java/dev/ringworld/client/ProductionLifecycleTestClient.java').read_text(encoding='utf-8')
        tick = client[client.index('public boolean tick(Minecraft client)'):client.index('private void captureProductionBaseline')]
        self.assertIn('server.execute(() -> RingWorldProductionLifecycleTest.markPlayerForSafety(playerId))', tick)
        self.assertNotIn('markClientReady', tick)
        self.assertLess(tick.index('markPlayerForSafety'), tick.index('switch (stage)'))
        baseline = client[client.index('private void captureProductionBaseline'):]
        self.assertLess(baseline.index('!atlas.isComplete()'), baseline.index('markClientReady'))

    def test_real_coordinator_protects_only_registered_live_player_across_reopen(self):
        java_home = Path(os.environ.get('JAVA_HOME', ''))
        suffix = '.exe' if os.name == 'nt' else ''
        javac = str(java_home / 'bin' / ('javac' + suffix))
        java = str(java_home / 'bin' / ('java' + suffix))
        if not Path(javac).is_file():
            javac, java = shutil.which('javac'), shutil.which('java')
        self.assertTrue(javac and java, 'JDK is required for the lifecycle regression')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in STUBS.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source, encoding='utf-8')
            coordinator = ROOT / 'src/main/java/dev/ringworld/server/RingWorldProductionLifecycleTest.java'
            result = subprocess.run([javac, '-d', str(root), *map(str, root.rglob('*.java')), str(coordinator)], capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run([java, '-cp', str(root), 'SafetyRegression'], capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
