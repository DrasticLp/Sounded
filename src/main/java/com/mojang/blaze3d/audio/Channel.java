package com.mojang.blaze3d.audio;

import com.drastic.sounded.SoundExecutor;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@OnlyIn(Dist.CLIENT)
public class Channel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int QUEUED_BUFFER_COUNT = 4;
    public static final int BUFFER_DURATION_SECONDS = 1;
    private AtomicInteger source;
    private final AtomicBoolean initialized = new AtomicBoolean(true);
    private int streamingBufferSize = 16384;
    @Nullable
    private AudioStream stream;
    private boolean music = false;
    private boolean sourceSet = false;

    @Nullable
    static Channel create() {
        return new Channel();
    }

    public Channel() {
        this.source = new AtomicInteger();
        this.source.set(-1);
    }

    public void destroy() {
        this.getExecutor().execute(() -> {
            if (this.initialized.compareAndSet(true, false)) {
                AL10.alSourceStop(this.source.get());
                OpenAlUtil.checkALError("Stop");
                if (this.stream != null) {
                    try {
                        this.stream.close();
                    } catch (IOException ioexception) {
                        LOGGER.error("Failed to close audio stream", (Throwable) ioexception);
                    }

                    this.removeProcessedBuffers();
                    this.stream = null;
                }

                AL10.alDeleteSources(new int[]{this.source.get()});

                OpenAlUtil.checkALError("Cleanup");
            }
        });
    }

    public void play(@Nullable SoundBuffer b, boolean static_, @Nullable AudioStream s) {
        this.play(b, static_, s, false);
    }

    public void playMusic(@Nullable SoundBuffer b, boolean static_, @Nullable AudioStream s) {
        this.play(b, static_, s, true);
    }

    public void play(@Nullable SoundBuffer b, boolean static_, @Nullable AudioStream s, boolean music) {
        this.music = music;

        this.getExecutor().execute(() -> {
            if (static_)
                this.attachStaticBuffer(b, false);
            else this.attachBufferStream(s, false);

            AL10.alSourcePlay(this.source.get());
        });
    }

    private int getState() {
        AtomicInteger i = new AtomicInteger();
        this.getExecutor().execute(() -> {
            i.set(!this.initialized.get() ? 4116 : AL10.alGetSourcei(this.source.get(), 4112));
        });

        return i.get();
    }

    public void pause() {
        this.getExecutor().execute(() -> {
            if ((!this.initialized.get() ? 4116 : AL10.alGetSourcei(this.source.get(), 4112)) == 4114)
                AL10.alSourcePause(this.source.get());
        });
    }

    public void unpause() {
        this.getExecutor().execute(() -> {
            if ((!this.initialized.get() ? 4116 : AL10.alGetSourcei(this.source.get(), 4112)) == 4115)
                AL10.alSourcePlay(this.source.get());
        });
    }

    public void stop() {
        this.getExecutor().execute(() -> {
            if (this.initialized.get())
                AL10.alSourceStop(this.source.get());
            OpenAlUtil.checkALError("Stop");
        });
    }

    public boolean playing() {
        return this.getState() == 4114;
    }

    public boolean stopped() {
        return this.getState() == 4116;
    }

    public void setSelfPosition(Vec3 p_83655_) {
        this.getExecutor().execute(() -> AL10.alSourcefv(this.source.get(), 4100, new float[]{(float) p_83655_.x, (float) p_83655_.y, (float) p_83655_.z}));
    }

    public void setPitch(float p_83651_) {
        this.getExecutor().execute(() -> AL10.alSourcef(this.source.get(), 4099, p_83651_));
    }

    public void setLooping(boolean p_83664_) {
        this.getExecutor().execute(() -> AL10.alSourcei(this.source.get(), 4103, p_83664_ ? 1 : 0));
    }

    public void setVolume(float p_83667_) {
        this.getExecutor().execute(() -> AL10.alSourcef(this.source.get(), 4106, p_83667_));
    }

    public void disableAttenuation() {
        this.getExecutor().execute(() -> AL10.alSourcei(this.source.get(), 53248, 0));
    }

    private SoundExecutor getExecutor() {

        SoundExecutor exec = (this.music ? Minecraft.getInstance().getSoundManager().soundEngine.musicExecutor : Minecraft.getInstance().getSoundManager().soundEngine.soundExecutor);

        if (this.source.get() < 0 && !this.sourceSet) {
            this.generateNewSource();
        }

        return exec;
    }

    public int generateNewSource() {
        SoundExecutor exec = (this.music ? Minecraft.getInstance().getSoundManager().soundEngine.musicExecutor : Minecraft.getInstance().getSoundManager().soundEngine.soundExecutor);

        exec.execute(() -> {
            int[] aint = new int[1];
            AL10.alGenSources(aint);
            this.source.set(aint[0]);
        });

        this.sourceSet = true;
        return this.source.get();
    }

    public void linearAttenuation(float p_83674_) {
        this.getExecutor().execute(() -> {
            AL10.alSourcei(this.source.get(), 53248, 53251);
            AL10.alSourcef(this.source.get(), 4131, p_83674_);
            AL10.alSourcef(this.source.get(), 4129, 1.0F);
            AL10.alSourcef(this.source.get(), 4128, 0.0F);
        });
    }

    public void setRelative(boolean p_83671_) {
        this.getExecutor().execute(() -> AL10.alSourcei(this.source.get(), 514, p_83671_ ? 1 : 0));
    }

    public void attachStaticBuffer(SoundBuffer p_83657_, boolean threaded) {
        if (threaded)
            this.getExecutor().execute(() -> p_83657_.getAlBuffer().ifPresent((p_83676_) -> {
                AL10.alSourcei(this.source.get(), 4105, p_83676_);
            }));
        else p_83657_.getAlBuffer().ifPresent((p_83676_) -> {
            AL10.alSourcei(this.source.get(), 4105, p_83676_);
        });
    }

    public void attachBufferStream(AudioStream p_83659_, boolean threaded) {
        this.stream = p_83659_;
        AudioFormat audioformat = p_83659_.getFormat();
        this.streamingBufferSize = calculateBufferSize(audioformat, 1);
        this.pumpBuffers(4, threaded);
    }

    private static int calculateBufferSize(AudioFormat p_83661_, int p_83662_) {
        return (int) ((float) (p_83662_ * p_83661_.getSampleSizeInBits()) / 8.0F * (float) p_83661_.getChannels() * p_83661_.getSampleRate());
    }

    private void pumpBuffers(int p_83653_, boolean threaded) {
        if (threaded)
            this.getExecutor().execute(() -> {
                this.pump(p_83653_);
            });
        else {
            this.pump(p_83653_);
        }
    }

    private void pump(int p_83653_) {
        if (this.stream != null) {
            try {
                for (int i = 0; i < p_83653_; ++i) {
                    ByteBuffer bytebuffer = this.stream.read(this.streamingBufferSize);
                    if (bytebuffer != null) {
                        (new SoundBuffer(bytebuffer, this.stream.getFormat())).releaseAlBuffer().ifPresent((p_83669_) -> {
                            AL10.alSourceQueueBuffers(this.source.get(), new int[]{p_83669_});
                        });
                    }
                }
            } catch (IOException ioexception) {
                LOGGER.error("Failed to read from audio stream", (Throwable) ioexception);
            }
        }
    }

    public void updateStream() {
        this.getExecutor().execute(() -> {
            if (this.stream != null) {
                int i = this.removeProcessedBuffers();
                this.pumpBuffers(i, false);
            }
        });
    }

    private int removeProcessedBuffers() {
        int i = AL10.alGetSourcei(this.source.get(), 4118);
        if (i > 0) {
            int[] aint = new int[i];
            AL10.alSourceUnqueueBuffers(this.source.get(), aint);
            OpenAlUtil.checkALError("Unqueue buffers");
            AL10.alDeleteBuffers(aint);
            OpenAlUtil.checkALError("Remove processed buffers");
        }
        return i;
    }
}