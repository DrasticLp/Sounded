package com.drastic.sounded;

import net.minecraft.client.Minecraft;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTThreadLocalContext;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SoundExecutor {

    private Executor executor;
    public AtomicLong context;

    public SoundExecutor(@Nullable String baseDevice, @Nullable long context) {
        this.context = new AtomicLong();
        this.executor = Executors.newSingleThreadExecutor();
        if (baseDevice != null)
            this.setDevice(baseDevice);
        else
            this.context.set(context);

    }

    public void setDevice(String s) {
        this.executor.execute(() -> {
            long device = ALC10.alcOpenDevice(s);
            long context = ALC10.alcCreateContext(device, new int[]{0});

            this.context.set(context);
            //EXTThreadLocalContext.alcSetThreadContext(this.context.get());
        });
    }

    public void execute(Runnable runnable) {
        this.executor.execute(() -> {
            long old = EXTThreadLocalContext.alcGetThreadContext();
            EXTThreadLocalContext.alcSetThreadContext(this.context.get());
            runnable.run();
            EXTThreadLocalContext.alcSetThreadContext(old);
        });
    }
}
