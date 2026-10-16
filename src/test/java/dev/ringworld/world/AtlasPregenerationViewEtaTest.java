package dev.ringworld.world;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class AtlasPregenerationViewEtaTest {
    @ParameterizedTest
    @EnumSource(AtlasPregenerationState.class)
    void onlyRunningWorkDisplaysItsProjectedCompletionTime(AtlasPregenerationState state) {
        var progress = new AtlasPregenerationProgress(state, 10, 100, 100, 1000, 40,
                Duration.ofSeconds(5), Optional.of(Duration.ofSeconds(22)), Optional.empty());
        var status = new AtlasPregenerationStatus(42L, 2048, 128, 11, 1,
                100, 10, progress, true, Optional.empty());
        String expected = state == AtlasPregenerationState.RUNNING ? "22s"
                : state == AtlasPregenerationState.COMPLETE ? "0s" : "Unknown";
        assertEquals(expected, AtlasPregenerationView.from(status).eta());
        assertEquals(Optional.of(Duration.ofSeconds(22)), progress.eta(), "Display policy must not mutate transported progress");
    }
}
