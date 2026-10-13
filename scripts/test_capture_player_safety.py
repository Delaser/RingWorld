"""Execute the real copied-world capture bridge with Minecraft boundary doubles."""
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
public static class Log { public void error(String s,Object... a){} }}''',
    'net/minecraft/client/gui/screens/DeathScreen.java': 'package net.minecraft.client.gui.screens; public class DeathScreen {}',
    'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
public class Minecraft { public net.minecraft.server.level.ServerPlayer player; public Object screen;
public net.minecraft.server.MinecraftServer server; public net.minecraft.server.MinecraftServer getSingleplayerServer(){return server;} }''',
    'dev/ringworld/client/RingMinecraftClientAccess.java': '''package dev.ringworld.client;
class RingMinecraftClientAccess { static Object screen(net.minecraft.client.Minecraft c){return c.screen;} }''',
    'net/minecraft/world/level/GameType.java': 'package net.minecraft.world.level; public enum GameType { SURVIVAL, SPECTATOR }',
    'net/minecraft/world/entity/Relative.java': 'package net.minecraft.world.entity; public enum Relative { X }',
    'net/minecraft/world/level/gamerules/GameRules.java': '''package net.minecraft.world.level.gamerules;
public class GameRules { public static final Object ADVANCE_TIME=new Object(); public void set(Object k,boolean b,Object s){} }''',
    'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;
public class ServerLevel { public net.minecraft.world.level.gamerules.GameRules getGameRules(){return new net.minecraft.world.level.gamerules.GameRules();} }''',
    'net/minecraft/server/level/ServerPlayer.java': '''package net.minecraft.server.level;
public class ServerPlayer { public final java.util.UUID id=java.util.UUID.randomUUID(); public boolean alive=true;
public int modeChanges; public net.minecraft.world.level.GameType mode=net.minecraft.world.level.GameType.SURVIVAL;
public java.util.UUID getUUID(){return id;} public boolean isAlive(){return alive;}
public void setGameMode(net.minecraft.world.level.GameType m){mode=m;modeChanges++;}
public void teleportTo(Object... a){} public float getYRot(){return 0;} public float getXRot(){return 0;} }''',
    'net/minecraft/server/MinecraftServer.java': '''package net.minecraft.server;
public class MinecraftServer {
public final java.util.Map<java.util.UUID,net.minecraft.server.level.ServerPlayer> players=new java.util.HashMap<>();
public final java.util.List<Runnable> pending=new java.util.ArrayList<>(); public int requests;
public void execute(Runnable r){pending.add(r);requests++;} public void runPending(){for(var r:java.util.List.copyOf(pending))r.run();pending.clear();}
public MinecraftServer getPlayerList(){return this;} public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID id){return players.get(id);}
public net.minecraft.server.level.ServerLevel overworld(){return new net.minecraft.server.level.ServerLevel();}
public MinecraftServer createCommandSourceStack(){return this;} public Object operatorUserPermissions(){return this;}
public MinecraftServer withPermission(Object p){return this;} public MinecraftServer withSuppressedOutput(){return this;}
public MinecraftServer getCommands(){return this;} public void performPrefixedCommand(Object s,String c){} }''',
    'dev/ringworld/client/CaptureSafetyRegression.java': '''package dev.ringworld.client;
import net.minecraft.client.Minecraft; import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer; import net.minecraft.world.level.GameType;
public class CaptureSafetyRegression {
static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
static Minecraft client(){var c=new Minecraft();c.server=new MinecraftServer();c.player=new ServerPlayer();c.server.players.put(c.player.id,c.player);return c;}
public static void main(String[] args){
var c=client(); var other=new ServerPlayer();c.server.players.put(other.id,other);
var safety=new RingIntegratedCaptureControl.PlayerSafety();var errors=new java.util.ArrayList<String>();
check(!safety.ensure(c,errors::add),"warmup advanced before server protection");
check(c.player.mode==GameType.SURVIVAL,"player mutated off server thread");
check(!safety.ensure(c,errors::add)&&c.server.requests==1,"duplicate server requests");
c.server.runPending();check(safety.ensure(c,errors::add),"warmup did not become ready");
check(c.player.mode==GameType.SPECTATOR,"survival player exposed during Atlas wait");
check(other.mode==GameType.SURVIVAL,"unrelated player changed");
for(int i=0;i<2400;i++)check(safety.ensure(c,errors::add),"long warmup lost protection");
check(c.player.modeChanges==1&&errors.isEmpty(),"redundant mutation or unexpected error");
c.player.alive=false;check(!safety.ensure(c,errors::add)&&!errors.isEmpty(),"death after readiness ignored");
var dead=client();dead.player.alive=false;var deadSafety=new RingIntegratedCaptureControl.PlayerSafety();errors.clear();
check(!deadSafety.ensure(dead,errors::add)&&dead.server.requests==0&&!dead.player.alive&&!errors.isEmpty(),"dead player changed or resurrected");
var screen=client();screen.screen=new net.minecraft.client.gui.screens.DeathScreen();var shown=screen.screen;errors.clear();
check(!new RingIntegratedCaptureControl.PlayerSafety().ensure(screen,errors::add)&&screen.screen==shown&&screen.server.requests==0&&!errors.isEmpty(),"death screen dismissed or ignored");
var lost=client();var lostSafety=new RingIntegratedCaptureControl.PlayerSafety();errors.clear();lostSafety.ensure(lost,errors::add);
var serverPlayer=new ServerPlayer();serverPlayer.alive=false;lost.server.players.put(lost.player.id,serverPlayer);lost.server.runPending();
check(!lostSafety.ensure(lost,errors::add)&&!errors.isEmpty()&&serverPlayer.modeChanges==0&&!serverPlayer.alive,"server death ignored or resurrected");
var missing=client();var missingSafety=new RingIntegratedCaptureControl.PlayerSafety();errors.clear();missingSafety.ensure(missing,errors::add);missing.server.players.clear();missing.server.runPending();
check(!missingSafety.ensure(missing,errors::add)&&!errors.isEmpty(),"missing server player accepted");
var remote=client();remote.server=null;errors.clear();var remoteSafety=new RingIntegratedCaptureControl.PlayerSafety();remoteSafety.ensure(remote,errors::add);
check(!remoteSafety.ensure(remote,errors::add)&&!errors.isEmpty()&&remote.player.modeChanges==0,"non-integrated player changed");
}}''',
}


class CapturePlayerSafetyTest(unittest.TestCase):
    def test_real_bridge_protects_live_target_before_long_atlas_wait_and_rejects_death(self):
        java_home = Path(os.environ.get('JAVA_HOME', ''))
        suffix = '.exe' if os.name == 'nt' else ''
        javac = str(java_home / 'bin' / ('javac' + suffix))
        java = str(java_home / 'bin' / ('java' + suffix))
        if not Path(javac).is_file():
            javac, java = shutil.which('javac'), shutil.which('java')
        self.assertTrue(javac and java, 'JDK is required for the capture regression')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in STUBS.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source, encoding='utf-8')
            bridge = ROOT / 'src/client/java/dev/ringworld/client/RingIntegratedCaptureControl.java'
            result = subprocess.run([javac, '-d', str(root), *map(str, root.rglob('*.java')), str(bridge)], capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run([java, '-cp', str(root), 'dev.ringworld.client.CaptureSafetyRegression'], capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_every_capture_client_arms_safety_before_screen_and_atlas_gates(self):
        for relative in (
            'src/client/java/dev/ringworld/client/RingProjectionCaptureClient.java',
            'src/client/java/dev/ringworld/client/RingVisualParityCaptureClient.java',
            'src/versions/26.3/client/java/dev/ringworld/client/RingVisualParityCaptureClient.java',
        ):
            with self.subTest(client=relative):
                source = (ROOT / relative).read_text(encoding='utf-8')
                tick = source[source.index('public boolean tick(Minecraft client)'):]
                safety = tick.index('playerSafety.ensure(client, detail -> finish(client, false, detail))')
                self.assertLess(tick.index('if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false'), safety)
                self.assertLess(tick.index('if (!ensureWorldOpen(client)) return true'), safety)
                self.assertLess(safety, tick.index('RingMinecraftClientAccess.screen(client)'))
                self.assertLess(safety, tick.index('ClientRingState.geometry()'))


    def test_layout_switch_protects_each_new_player_without_changing_world_assertions(self):
        source = (ROOT / 'src/client/java/dev/ringworld/client/LayoutSwitchTestClient.java').read_text()
        self.assertIn('(stage == 1 || stage == 3) && client.player != null', source)
        self.assertIn('!playerSafety.ensure(client, detail -> finish(client, false, detail))', source)
        second = source[source.index('private void startSecondWorld'):source.index('private void verifySecondWorld')]
        self.assertLess(second.index('playerSafety = new RingIntegratedCaptureControl.PlayerSafety()'),
                        second.index('client.createWorldOpenFlows().openWorld'))
        self.assertIn('disconnectClearedState && firstStorageVerified && expectedRelation', source)


if __name__ == '__main__':
    unittest.main()
