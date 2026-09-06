package dev.ringworld.world;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class RingIndustrialElementsTest {
    @Test void canonicalPlacementClosesTheRingAndCoversEveryKind() {
        for (int c : new int[]{2048,2064,16384}) for(int side=0;side<2;side++) {
            var kinds = new HashSet<Integer>();
            for(int x=0;x<c;x++) {
                var f=RingIndustrialElements.feature(x,c,8128,side);
                assertEquals(f,RingIndustrialElements.feature(x+c,c,8128,side));
                assertEquals(f,RingIndustrialElements.feature(x-c,c,8128,side));
                assertTrue(f.centerX()>=0&&f.centerX()<c);
                if(f.offsetX()==0)kinds.add(f.kind());
            }
            assertEquals(RingIndustrialElements.KIND_COUNT,kinds.size());
        }
    }
    @Test void featureIntervalsDoNotOverlapAndSidesDiffer() {
        int previous=-1000,different=0;
        for(int x=0;x<16384;x++) {
            var a=RingIndustrialElements.feature(x,16384,17,0);
            var b=RingIndustrialElements.feature(x,16384,17,1);
            if(a.offsetX()==0){assertTrue(x-previous>2*RingIndustrialElements.HALF_WIDTH);previous=x;}
            if(!a.equals(b))different++;
        }
        assertTrue(different>8000);
    }
    @Test void allElementsAreNonemptyAndStayWithinTheirBounds() {
        for(int kind=0;kind<RingIndustrialElements.KIND_COUNT;kind++)for(int height:new int[]{24,28,33,80})for(int thickness:new int[]{2,3,7,32}) {
            int changed=0;
            for(int x=-20;x<=20;x++)for(int y=-1;y<=34;y++)for(int z=-4;z<=4;z++){
                int roll=RingIndustrialElements.roll(new RingIndustrialElements.Feature(kind,100,x),y,height,z,thickness);
                if(roll==RingIndustrialElements.UNCHANGED)continue;
                changed++;
                assertTrue(Math.abs(x)<=18&&y>=0&&y<Math.min(33,height)&&z>=-Math.min(3,thickness-1)&&z<=3);
                assertTrue(roll>=-1&&roll<100);
                if(z==-(thickness-1))assertNotEquals(-1,roll,"never perforate the outer backing");
            }
            assertTrue(changed>0,"every accepted kind should survive height scaling");
        }
    }
    @Test void machineryAndChannelsHaveDistinctRecessedShapesWithoutBraces() {
        var shapes = new HashSet<String>();
        for (int kind = 6; kind < RingIndustrialElements.KIND_COUNT; kind++) {
            var shape = new StringBuilder();
            boolean cavity = false;
            for (int x = -18; x <= 18; x++) for (int y = 0; y < 33; y++) {
                var f = new RingIndustrialElements.Feature(kind, 100, x);
                for (int z = -3; z <= 0; z++) {
                    int roll = RingIndustrialElements.roll(f, y, 33, z, 7);
                    shape.append(roll).append(',');
                    cavity |= roll == RingIndustrialElements.AIR;
                }
                for (int z = 1; z <= 3; z++)
                    assertEquals(RingIndustrialElements.UNCHANGED,
                            RingIndustrialElements.roll(f, y, 33, z, 7));
            }
            assertTrue(cavity);
            assertTrue(shapes.add(shape.toString()), "variants must have different geometry");
        }
    }
    @Test void hiddenOrThinWallsAndLegacyStylesRemainPlain() {
        var f=new RingIndustrialElements.Feature(1,100,0);
        assertEquals(RingIndustrialElements.UNCHANGED,RingIndustrialElements.roll(f,1,23,0,7));
        assertEquals(RingIndustrialElements.UNCHANGED,RingIndustrialElements.roll(f,1,33,0,1));
        assertFalse(RingIndustrialElements.enabled(RingWallStyle.custom(7,RingWallStyle.Palette.INDUSTRIAL,RingWallStyle.Pattern.PANELS,10)));
        assertFalse(RingIndustrialElements.enabled(RingWallStyle.custom(7,RingWallStyle.Palette.WEATHERED,RingWallStyle.Pattern.ENGINEERED,10)));
        assertTrue(RingIndustrialElements.enabled(RingWallStyle.Preset.INDUSTRIAL_SUPERSTRUCTURE.style()));
    }
}
