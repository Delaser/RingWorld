"""Exercise the real 1.21.1 GPU upload owner with recording GL/type stubs.

This checks byte ranges, publication and failure ownership, not native GL
latency or Minecraft ABI. Packaged dual-loader rendering remains required.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'com/mojang/blaze3d/vertex/VertexFormat.java': '''package com.mojang.blaze3d.vertex;
public class VertexFormat { public enum Mode { TRIANGLES, QUADS } public int getVertexSize(){return 24;} }''',
    'com/mojang/blaze3d/vertex/DefaultVertexFormat.java': '''package com.mojang.blaze3d.vertex;
public class DefaultVertexFormat {public static final VertexFormat POSITION_TEX_COLOR=new VertexFormat();}''',
    'com/mojang/blaze3d/vertex/ByteBufferBuilder.java': '''package com.mojang.blaze3d.vertex;
public class ByteBufferBuilder implements AutoCloseable {public int closes; public ByteBufferBuilder(int size){} public void close(){closes++;}}''',
    'com/mojang/blaze3d/vertex/BufferBuilder.java': '''package com.mojang.blaze3d.vertex;
public class BufferBuilder {public BufferBuilder(ByteBufferBuilder a,VertexFormat.Mode b,VertexFormat c){}
public BufferBuilder addVertex(float x,float y,float z){return this;} public BufferBuilder setUv(float u,float v){return this;}
public BufferBuilder setColor(int c){return this;} public MeshData buildOrThrow(){throw new UnsupportedOperationException();}}''',
    'com/mojang/blaze3d/vertex/MeshData.java': '''package com.mojang.blaze3d.vertex;
import java.nio.ByteBuffer; public class MeshData implements AutoCloseable {
public record DrawState(VertexFormat format,VertexFormat.Mode mode){}
public int closes; public ByteBuffer bytes,indices; private final DrawState state;
public MeshData(ByteBuffer bytes,DrawState state){this.bytes=bytes;this.state=state;}
public ByteBuffer vertexBuffer(){return bytes;} public ByteBuffer indexBuffer(){return indices;}
public DrawState drawState(){return state;} public void close(){if(++closes>1)throw new AssertionError("MeshData closed twice");}}
''',
    'com/mojang/blaze3d/vertex/VertexBuffer.java': '''package com.mojang.blaze3d.vertex;
import org.lwjgl.opengl.GL15; public class VertexBuffer implements AutoCloseable {
public enum Usage {STATIC} public static VertexBuffer last; public int closes; public boolean failClose; public MeshData.DrawState state;
public VertexBuffer(Usage usage){last=this;} public void bind(){} public static void unbind(){}
public void upload(MeshData data){try(data){state=data.drawState();if(data.vertexBuffer()!=null)throw new AssertionError("Unbounded transfer");GL15.bound=17;}}
public void close(){if(++closes>1)throw new AssertionError("GPU closed twice");if(failClose)throw new IllegalStateException("close");}}
''',
    'com/mojang/blaze3d/systems/RenderSystem.java': '''package com.mojang.blaze3d.systems;
public class RenderSystem {public static void assertOnRenderThread(){}}''',
    'com/mojang/blaze3d/platform/GlStateManager.java': '''package com.mojang.blaze3d.platform;
public class GlStateManager {public static void _glBindBuffer(int target,int id){org.lwjgl.opengl.GL15.bound=id;}}''',
    'org/lwjgl/opengl/GL11.java': '''package org.lwjgl.opengl;
public class GL11 {public static int glGetInteger(int key){return GL15.bound;}}''',
    'org/lwjgl/opengl/GL15.java': '''package org.lwjgl.opengl;
import java.nio.ByteBuffer; import java.util.*;
public class GL15 {public static final int GL_ARRAY_BUFFER=1,GL_ARRAY_BUFFER_BINDING=2,GL_STATIC_DRAW=3;
public static int bound; public static boolean failAllocate,failTransfer; public static byte[] destination;
public static List<Integer> lengths=new ArrayList<>(); public static List<Long> offsets=new ArrayList<>();
public static void glBufferData(int target,long size,int usage){if(bound!=17)throw new AssertionError("Wrong buffer");if(failAllocate)throw new IllegalStateException("allocation");destination=new byte[Math.toIntExact(size)];}
public static void glBufferSubData(int target,long offset,ByteBuffer bytes){if(bound!=17)throw new AssertionError("Wrong buffer");if(failTransfer)throw new IllegalStateException("transfer");lengths.add(bytes.remaining());offsets.add(offset);bytes.get(destination,Math.toIntExact(offset),bytes.remaining());}}
''',
    'dev/ringworld/world/RingSurfaceMesh.java': '''package dev.ringworld.world;
public class RingSurfaceMesh {public interface VertexConsumer {void sideColor(int rgb);void intrinsic(float x,float y,float z);void vertex(float x,float y,float z,float u,float v);}
public static class Mesh {public int vertexCount(){return 3;}public void emitTriangles(VertexConsumer consumer){}}}''',
    'dev/ringworld/client/render/UploadRegression.java': r'''
package dev.ringworld.client.render;
import java.nio.ByteBuffer; import java.util.Arrays;
import com.mojang.blaze3d.vertex.*; import org.lwjgl.opengl.GL15;
public class UploadRegression {
 static void require(boolean ok){if(!ok)throw new AssertionError();}
 static void rejects(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalStateException expected){}}
 public static void main(String[] args) {
  int budget=RingSurfaceGpu.Upload.BYTES_PER_RENDER_CALL;
  int count=(2*budget+120)/24, size=count*24;
  ByteBuffer bytes=ByteBuffer.allocate(size+16);bytes.position(8).limit(8+size);
  byte[] expected=new byte[size];for(int i=0;i<size;i++){expected[i]=(byte)(i*31);bytes.put(i+8,expected[i]);}
  var allocator=new ByteBufferBuilder(size);
  var data=new MeshData(bytes,new MeshData.DrawState(DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.TRIANGLES));
  var packed=new RingSurfaceGpu.PackedMesh(allocator,data,count);
  if(args[0].equals("invalid")) {
   data.indices=ByteBuffer.allocate(4);
   try{RingSurfaceGpu.beginUpload(packed);throw new AssertionError();}catch(IllegalArgumentException expectedFailure){}
   require(VertexBuffer.last==null);packed.close();require(data.closes==1&&allocator.closes==1);return;
  }
  if(args[0].equals("allocation")) {
   GL15.failAllocate=true;rejects(()->RingSurfaceGpu.beginUpload(packed));
   require(VertexBuffer.last.closes==1&&data.closes==0&&GL15.bound==0);packed.close();return;
  }
  var upload=RingSurfaceGpu.beginUpload(packed);var gpu=VertexBuffer.last;
  require(gpu.state.equals(data.drawState())&&data.closes==0&&GL15.bound==0);
  rejects(upload::take);require(!upload.complete());
  if(args[0].equals("transfer")) {
   GL15.failTransfer=true;rejects(upload::advance);require(GL15.bound==0&&!upload.complete());
   GL15.failTransfer=false; // Failed slice must not advance its offset.
   upload.advance();require(GL15.offsets.getFirst()==0);upload.close();packed.close();
  } else if(args[0].equals("abort")) {
   upload.advance();upload.close();upload.close();packed.close();packed.close();
   require(GL15.lengths.size()==1&&gpu.closes==1&&data.closes==1&&allocator.closes==1);
   rejects(upload::advance);rejects(upload::take);
  } else {
   int calls=0;while(!upload.complete()){upload.advance();require(++calls<=3);require(GL15.bound==0);}
   upload.advance();require(calls==3&&GL15.lengths.size()==3);
   int total=0;for(int i=0;i<3;i++){require(GL15.offsets.get(i)==total&&GL15.lengths.get(i)<=budget);total+=GL15.lengths.get(i);}
   require(total==size&&Arrays.equals(GL15.destination,expected)&&bytes.position()==8&&bytes.limit()==8+size);
   require(upload.take()==gpu&&gpu.closes==0&&data.closes==0);
   rejects(upload::take);rejects(upload::advance);upload.close();require(gpu.closes==0);
   packed.close();require(data.closes==1&&allocator.closes==1);gpu.close();
  }
  require(gpu.closes==1&&data.closes==1&&allocator.closes==1);
 }
}''',
}


class SurfaceGpuUploadLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        cls.build = Path(cls.temp.name)
        java_home = os.environ.get('JAVA_HOME')
        cls.javac = str(Path(java_home)/'bin/javac.exe') if java_home and os.name == 'nt' else shutil.which('javac')
        cls.java = str(Path(java_home)/'bin/java.exe') if java_home and os.name == 'nt' else shutil.which('java')
        if not cls.javac or not cls.java:
            raise RuntimeError('JDK 21 is required; this ownership test must not silently skip')
        renderer = (ROOT/'src/client/java/dev/ringworld/client/render/RingSurfaceTextureRenderer.java').read_text()
        start = renderer.index('    private record StagedTextureBuild(')
        end = renderer.index('\n    }', start) + len('\n    }')
        actual_record = renderer[start:end]
        stage_harness = '''package dev.ringworld.client.render;
import java.nio.ByteBuffer; import com.mojang.blaze3d.vertex.*;
public class StageCloseRegression {
static class TextureBuild {int closes;boolean fail;void close(){closes++;if(fail)throw new IllegalStateException("build");}}
ACTUAL_RECORD
public static void main(String[] args){
int scenario=Integer.parseInt(args[0]);var data=new MeshData(ByteBuffer.allocate(72),new MeshData.DrawState(DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.TRIANGLES));
var packed=new RingSurfaceGpu.PackedMesh(new ByteBufferBuilder(72),data,3);var upload=RingSurfaceGpu.beginUpload(packed);
var gpu=VertexBuffer.last;gpu.failClose=scenario==1;var build=new TextureBuild();build.fail=scenario==2;
boolean failed=false;try{new StagedTextureBuild(build,upload).close();}catch(IllegalStateException expected){failed=true;}
if(gpu.closes!=1||build.closes!=1||failed!=(scenario!=0))throw new AssertionError("staged ownership");packed.close();
}}
'''.replace('ACTUAL_RECORD', actual_record)
        sources = []
        for name, code in {**STUBS, 'dev/ringworld/client/render/StageCloseRegression.java': stage_harness}.items():
            path = cls.build/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(code)
            sources.append(str(path))
        sources += [str(ROOT/'src/client/java/dev/ringworld/client/render/RingSurfaceGpu.java'),
                    str(ROOT/'src/main/java/dev/ringworld/world/RingSurfaceMeshBytes.java')]
        subprocess.run([cls.javac, '-d', str(cls.build), *sources], check=True, capture_output=True, text=True, timeout=60)

    def run_case(self, name):
        subprocess.run([self.java, '-cp', str(self.build), 'dev.ringworld.client.render.UploadRegression', name],
                       check=True, capture_output=True, text=True, timeout=30)

    def test_exact_bounded_bytes_and_single_publication(self): self.run_case('complete')
    def test_abandonment_retires_both_owners_once(self): self.run_case('abort')
    def test_failed_transfer_does_not_skip_bytes(self): self.run_case('transfer')
    def test_allocation_failure_retires_gpu_without_consuming_cpu_owner(self): self.run_case('allocation')
    def test_indexed_input_is_rejected_before_allocation(self): self.run_case('invalid')

    def run_stage_case(self, scenario):
        subprocess.run([self.java, '-cp', str(self.build), 'dev.ringworld.client.render.StageCloseRegression', str(scenario)],
                       check=True, capture_output=True, text=True, timeout=30)

    def test_actual_staged_owner_normal_close(self): self.run_stage_case(0)
    def test_actual_staged_owner_gpu_failure_closes_build(self): self.run_stage_case(1)
    def test_actual_staged_owner_build_failure_closes_gpu(self): self.run_stage_case(2)


if __name__ == '__main__':
    unittest.main()
