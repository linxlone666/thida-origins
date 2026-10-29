package handoff.example;

import dev.thida.ThidaClientEffects;
import dev.thida.ThidaEffectBinding;
import dev.thida.ThidaEffectEvent;
import dev.thida.ThidaPassiveEffectAdapter;
import net.minecraft.nbt.CompoundTag;

/** Client setup only. These facades deliberately do not invent an AAA API. */
public final class EffectBackendExample {
    public interface Playback extends AutoCloseable {
        void update(ThidaPassiveEffectAdapter.Frame frame);
        void close();
    }

    public interface Backend extends AutoCloseable {
        // Return null for unsupported or missing resources.
        Playback start(ThidaEffectEvent event, ThidaEffectBinding binding,
                       ThidaPassiveEffectAdapter.Frame frame);
        void reload();
        void close();
    }

    public static void install(Backend backend) {
        ThidaClientEffects.installAdapter(new ThidaPassiveEffectAdapter() {
            public void onPassiveEvent(CompoundTag event) {
                // Observe only. Managed start/Handle is the sole rendering path.
            }

            public Handle start(ThidaEffectEvent event, ThidaEffectBinding binding, Frame frame) {
                Playback playback = backend.start(event, binding, frame);
                if (playback == null) return null;
                return new Handle() {
                    private boolean closed;

                    public void update(Frame current) {
                        if (!closed) playback.update(current);
                    }

                    public void close() {
                        if (closed) return;
                        closed = true;
                        playback.close();
                    }
                };
            }

            public void onResourceReload() { backend.reload(); }
            public void close() { backend.close(); }
        });
    }

    public static void uninstall() {
        ThidaClientEffects.installAdapter(null);
    }

    private EffectBackendExample() {}
}
