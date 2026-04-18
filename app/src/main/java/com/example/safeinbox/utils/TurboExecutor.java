package com.example.safeinbox.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centered Executor Service to handle all background tasks with optimal thread count.
 * Prevents thread explosion and memory overhead.
 */
public class TurboExecutor {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int THREAD_POOL_SIZE = Math.max(4, CPU_COUNT);

    private static TurboExecutor instance;
    private final ExecutorService executorService;

    private TurboExecutor() {
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "TurboTask-" + count.getAndIncrement());
            }
        });
    }

    public static synchronized TurboExecutor getInstance() {
        if (instance == null) {
            instance = new TurboExecutor();
        }
        return instance;
    }

    public void execute(Runnable task) {
        executorService.execute(task);
    }

    public ExecutorService getExecutor() {
        return executorService;
    }
}
