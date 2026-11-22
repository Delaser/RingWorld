package dev.ringworld.client.render;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyCycle;
import dev.ringworld.world.RingSkyProfile;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingSkyViewTest {
    @Test void deferredDrawsKeepTheirOwnEyeAndRestoreTheOuterViewOnFailure() {
        var geometry = new RingGeometry(128, 2048);
        var daylight = RingSkyCycle.sunVisual(6000);
        var above = new RingSkyView(new WeakReference<>(null), geometry, new Vec3(2, 120, 0), FogType.NONE,
                RingSkyProfile.DEFAULT, daylight, 0, true);
        var below = new RingSkyView(new WeakReference<>(null), geometry, new Vec3(40, 45, 16), FogType.WATER,
                RingSkyProfile.DEFAULT, daylight, 0, false);
        Runnable dry = RingSkyView.bind(above, () -> assertSame(above, RingSkyView.current()));
        var failure = new IllegalStateException();
        Runnable otherDimension = RingSkyView.bind(null, () -> {
            assertNull(RingSkyView.current());
            throw failure;
        });

        RingSkyView.bind(below, () -> {
            dry.run();
            assertSame(below, RingSkyView.current());
            assertSame(failure, assertThrows(IllegalStateException.class, otherDimension::run));
            assertSame(below, RingSkyView.current());
        }).run();
        assertNull(RingSkyView.current());
    }
}
