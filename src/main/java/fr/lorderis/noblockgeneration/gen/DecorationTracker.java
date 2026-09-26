package fr.lorderis.noblockgeneration.gen;

import fr.lorderis.noblockgeneration.config.NbgConfig;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Arrays;

/**
 * Per-thread state describing what is currently being placed into the world during the decoration
 * step of a chunk. Chunks are generated on several worker threads at once, hence the thread local.
 *
 * <p>The context only exists while a targeted chunk is being decorated; outside of that window
 * {@link #active()} returns {@code null} and every hook turns into an early return.
 */
public final class DecorationTracker {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private DecorationTracker() {
    }

    public static void start(NbgConfig config, ChunkAccess chunk) {
        ACTIVE.set(new Context(config, chunk));
    }

    public static Context active() {
        return ACTIVE.get();
    }

    public static void clear() {
        ACTIVE.remove();
    }

    public static final class Context {
        private final NbgConfig config;
        private final ChunkAccess chunk;
        private int structureDepth;
        private boolean[] featureKeep = new boolean[16];
        private int featureDepth;

        private Context(NbgConfig config, ChunkAccess chunk) {
            this.config = config;
            this.chunk = chunk;
        }

        public void pushStructure() {
            this.structureDepth++;
        }

        public void popStructure() {
            if (this.structureDepth > 0) {
                this.structureDepth--;
            }
        }

        /**
         * Enters a feature. Its verdict is settled right away from its own type and id, or taken
         * over from the feature it is nested in (see {@link NbgConfig#keepsFeature}).
         */
        public void pushFeature(String type, String id) {
            boolean nested = this.featureDepth > 0;
            boolean enclosingKeeps = nested && this.featureKeep[this.featureDepth - 1];
            boolean keep = this.config.keepsFeature(type, id, nested, enclosingKeeps);
            if (this.featureDepth == this.featureKeep.length) {
                this.featureKeep = Arrays.copyOf(this.featureKeep, this.featureDepth * 2);
            }
            this.featureKeep[this.featureDepth++] = keep;
        }

        public void popFeature() {
            if (this.featureDepth > 0) {
                this.featureDepth--;
            }
        }

        /**
         * Whether blocks written right now must survive the stripping pass.
         *
         * <p>Structures win over features, so a village that plants its own trees or decorates
         * itself with a feature keeps everything. Otherwise the innermost feature decides, which is
         * what makes container features ({@code random_selector}, {@code root_system}, ...)
         * transparent: only the blocks a feature writes itself are judged by its own verdict.
         */
        public boolean shouldKeepWrites() {
            if (this.structureDepth > 0) {
                return this.config.keepStructures;
            }
            if (this.featureDepth > 0) {
                return this.config.keepFeatures && this.featureKeep[this.featureDepth - 1];
            }
            return false;
        }

        /** Whether a structure is being placed right now, rather than a loose feature. */
        public boolean isInStructure() {
            return this.structureDepth > 0;
        }

        public NbgConfig config() {
            return this.config;
        }

        /**
         * The chunk this recording window was opened for. Checked before stripping: an injection at
         * RETURN does not fire when decoration throws, so a context can outlive its own chunk, and
         * stripping the wrong chunk with someone else's marks would delete a whole village.
         */
        public ChunkAccess chunk() {
            return this.chunk;
        }
    }
}
