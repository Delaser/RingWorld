package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable, saved resolution of the optional built-in ocean-monument request.
 * It contains no loader or registry objects so Fabric and NeoForge can bind
 * the same policy to their worldgen adapters.
 */
public record RingMonumentResolution(Status status, @Nullable Candidate candidate, Reason reason) {
    public static final Codec<RingMonumentResolution> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(value -> Status.valueOf(value.toUpperCase(Locale.ROOT)),
                    value -> value.name().toLowerCase(Locale.ROOT)).fieldOf("status")
                    .forGetter(RingMonumentResolution::status),
            Candidate.CODEC.optionalFieldOf("candidate").forGetter(value -> java.util.Optional.ofNullable(value.candidate())),
            Codec.STRING.xmap(value -> Reason.valueOf(value.toUpperCase(Locale.ROOT)),
                    value -> value.name().toLowerCase(Locale.ROOT)).fieldOf("reason")
                    .forGetter(RingMonumentResolution::reason)
    ).apply(instance, (status, candidate, reason) -> new RingMonumentResolution(
            status, candidate.orElse(null), reason)));

    public RingMonumentResolution {
        switch (status) {
            case DISABLED -> {
                if (candidate != null || reason != Reason.NOT_REQUESTED) {
                    throw new IllegalArgumentException("disabled monument state must be not-requested");
                }
            }
            case PENDING -> {
                if (candidate != null || reason != Reason.NOT_YET_RESOLVED) {
                    throw new IllegalArgumentException("pending monument state must be unresolved");
                }
            }
            case SATISFIED -> {
                if (candidate == null || reason != Reason.VALIDATED) {
                    throw new IllegalArgumentException("satisfied monument state needs a validated candidate");
                }
            }
            case UNSATISFIED -> {
                if (candidate != null || reason == Reason.NOT_REQUESTED
                        || reason == Reason.NOT_YET_RESOLVED || reason == Reason.VALIDATED) {
                    throw new IllegalArgumentException("unsatisfied monument state needs a failure reason");
                }
            }
        }
    }

    public static RingMonumentResolution disabled() {
        return new RingMonumentResolution(Status.DISABLED, null, Reason.NOT_REQUESTED);
    }

    public static RingMonumentResolution pending() {
        return new RingMonumentResolution(Status.PENDING, null, Reason.NOT_YET_RESOLVED);
    }

    public static RingMonumentResolution satisfied(RingMonumentPlacement.Candidate candidate) {
        return new RingMonumentResolution(Status.SATISFIED,
                new Candidate(candidate.chunkX(), candidate.chunkZ()), Reason.VALIDATED);
    }

    public static RingMonumentResolution unsatisfied(Reason reason) {
        if (reason == Reason.VALIDATED || reason == Reason.NOT_REQUESTED || reason == Reason.NOT_YET_RESOLVED) {
            throw new IllegalArgumentException("unsatisfied monument resolution needs a failure reason");
        }
        return new RingMonumentResolution(Status.UNSATISFIED, null, reason);
    }

    public boolean isResolved() {
        return status == Status.SATISFIED || status == Status.UNSATISFIED || status == Status.DISABLED;
    }

    public enum Status { DISABLED, PENDING, SATISFIED, UNSATISFIED }

    /** Stable saved failure codes; do not replace a resolved failure with a new search on reload. */
    public enum Reason {
        NOT_REQUESTED,
        NOT_YET_RESOLVED,
        VALIDATED,
        BUILTIN_REGISTRY_UNAVAILABLE,
        NO_CANDIDATE_IN_BOUNDS,
        NO_CANDIDATE_MATCHED_BIOME,
        SEARCH_BUDGET_EXHAUSTED
    }

    public record Candidate(int chunkX, int chunkZ) {
        static final Codec<Candidate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("chunk_x").forGetter(Candidate::chunkX),
                Codec.INT.fieldOf("chunk_z").forGetter(Candidate::chunkZ)
        ).apply(instance, Candidate::new));
    }
}
