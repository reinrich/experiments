import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;


// jdk21_nb/bin/java -Djdk.virtualThreadScheduler.maxPoolSize=4 -Djdk.virtualThreadScheduler.parallelism=4 VTReentrantLockExample.java

// jdk21_nb/bin/jcmd jdk.compiler/com.sun.tools.javac.launcher.Main Thread.dump_to_file -overwrite out.txt

public class VTReentrantLockExample {

    public static final long K = 1024;
    public static final long M = 1024*K;
    public static final long G = 1024*M;

    public static final boolean USE_SYNC = true;
    public static final ReentrantLock LOCK = new ReentrantLock();

    public static volatile BlackholeCounter BHC;

    public static class BlackholeCounter {
        public volatile long counter = 1;
    }

    public static void main(String[] args) {
        final int writers = 40;
        final AtomicBoolean stop = new AtomicBoolean();
        //        ExecutorService executorService = Executors.newCachedThreadPool();
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        BHC = new BlackholeCounter();
        for (int i = 0; i < writers; i++) {
            if (USE_SYNC) {
                executorService.execute(() -> {
                        while (!stop.get()) {
                            synchronized (LOCK) {
                                BlackholeCounter old = BHC;
                                BHC = new BlackholeCounter();
                                consumeCPU(old);
                            }
                        }
                    });
            } else {
                executorService.execute(() -> {
                        while (!stop.get()) {
                            LOCK.lock();
                            BlackholeCounter old = BHC;
                            BHC = new BlackholeCounter();
                            consumeCPU(old);
                            LOCK.unlock();
                        }
                    });
            }
        }
        try {
            executorService.awaitTermination(600, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void consumeCPU(BlackholeCounter bhc) {
        while (bhc.counter < 4*M) {
            bhc.counter += 1;
        }
    }

}
