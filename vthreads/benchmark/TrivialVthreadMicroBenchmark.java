import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

/*
  Very simple microbenchmark that exercises virtual and platform threads.

  We use -XX:CompileCommand=dontinline,*::*dontinline to prevent inlining for
  better control how many frames are on stack when threads are switched

  $ uname -a
  Linux [...] 5.15.0-91-generic #101-Ubuntu SMP Tue Nov 14 13:30:08 UTC 2023 x86_64 x86_64 x86_64 GNU/Linux
  24 CPUs

  Platform Threads                                                                            Virtual Threads

  $ SETTINGS=(pthreads nogc 30000) ; \                                                        $ SETTINGS=(vthreads nogc 30000) ; \
    java -showversion -XX:+UseParallelGC -Xmx1200m -Xmn1g \                 java -showversion -XX:+UseParallelGC -Xmx1200m -Xmn1g \
           -XX:CompileCommand=dontinline,*::*dontinline TrivialVthreadMicroBenchmark.java ${SETTINGS[@]}            -XX:CompileCommand=dontinline,*::*dontinline TrivialVthreadMicroBenchmark.java ${SETTINGS[@]}
  CompileCommand: dontinline *.*dontinline bool dontinline = true                             CompileCommand: dontinline *.*dontinline bool dontinline = true
  openjdk version "21.0.3" 2024-04-16 LTS                                                     openjdk version "21.0.3" 2024-04-16 LTS
  OpenJDK Runtime Environment SapMachine (build 21.0.3+9-LTS)                                 OpenJDK Runtime Environment SapMachine (build 21.0.3+9-LTS)
  OpenJDK 64-Bit Server VM SapMachine (build 21.0.3+9-LTS, mixed mode, sharing)               OpenJDK 64-Bit Server VM SapMachine (build 21.0.3+9-LTS, mixed mode, sharing)
  main: All threads started in 8481 ms                                                        main: All threads started in 150 ms
  main: All threads finished in 3139 ms                                                       main: All threads finished in 532 ms
  main: All threads started in 3820 ms                                                        main: All threads started in 126 ms
  main: All threads finished in 718 ms                                                        main: All threads finished in 58 ms
  main: All threads started in 724 ms                                                         main: All threads started in 121 ms
  main: All threads finished in 636 ms                                                        main: All threads finished in 64 ms
  main: All threads started in 711 ms                                                         main: All threads started in 122 ms
  main: All threads finished in 664 ms                                                        main: All threads finished in 50 ms
  main: All threads started in 698 ms                                                         main: All threads started in 122 ms
  main: All threads finished in 672 ms                                                        main: All threads finished in 53 ms
  main: All threads started in 717 ms                                                         main: All threads started in 121 ms
  main: All threads finished in 671 ms                                                        main: All threads finished in 62 ms
  main: All threads started in 722 ms                                                         main: All threads started in 124 ms
  main: All threads finished in 696 ms                                                        main: All threads finished in 63 ms
  main: All threads started in 726 ms                                                         main: All threads started in 121 ms
  main: All threads finished in 705 ms                                                        main: All threads finished in 50 ms
  main: All threads started in 731 ms                                                         main: All threads started in 125 ms
  main: All threads finished in 705 ms                                                        main: All threads finished in 53 ms
  main: All threads started in 710 ms                                                         main: All threads started in 122 ms
  main: All threads finished in 701 ms                                                        main: All threads finished in 60 ms
  main: Shutdown in 253 ms                                                                    main: Shutdown in 0 ms
  (killed because it takes very long until the process has terminated

  Observations:

    - cannot scale beyond 30K platform threads
    - Platform thread pool takes very long to start up
    - Start-up in subsequent iterations is also much slower with pthreads even though they are pooled
    - Overall the performance of vthreads is an order of magnitude (or more) better
    - With pthreads it takes the vm extremely long to exit after shutdown of the ExecutorService

  Scaling up to 100000 threads (not possible with pthreads)

  No GC when all threads are started                                                           GC when all threads are started

  $ SETTINGS=(vthreads nogc 100000) ; \                                                       $ SETTINGS=(vthreads gc 100000) ; \
    java -showversion -XX:+UseParallelGC -Xmx1200m -Xmn1g \                 java -showversion -XX:+UseParallelGC -Xmx1200m -Xmn1g \
           -XX:CompileCommand=dontinline,*::*dontinline TrivialVthreadMicroBenchmark.java ${SETTINGS[@]}            -XX:CompileCommand=dontinline,*::*dontinline TrivialVthreadMicroBenchmark.java ${SETTINGS[@]}
  CompileCommand: dontinline *.*dontinline bool dontinline = true                             CompileCommand: dontinline *.*dontinline bool dontinline = true
  openjdk version "21.0.3" 2024-04-16 LTS                                                     openjdk version "21.0.3" 2024-04-16 LTS
  OpenJDK Runtime Environment SapMachine (build 21.0.3+9-LTS)                                 OpenJDK Runtime Environment SapMachine (build 21.0.3+9-LTS)
  OpenJDK 64-Bit Server VM SapMachine (build 21.0.3+9-LTS, mixed mode, sharing)               OpenJDK 64-Bit Server VM SapMachine (build 21.0.3+9-LTS, mixed mode, sharing)
  main: All threads started in 487 ms                                                         main: All threads started in 451 ms
  main: All threads finished in 1841 ms                                                       main: All threads finished in 2629 ms
  main: All threads started in 414 ms                                                         main: All threads started in 411 ms
  main: All threads finished in 260 ms                                                        main: All threads finished in 515 ms
  main: All threads started in 431 ms                                                         main: All threads started in 421 ms
  main: All threads finished in 239 ms                                                        main: All threads finished in 499 ms
  main: All threads started in 404 ms                                                         main: All threads started in 420 ms
  main: All threads finished in 333 ms                                                        main: All threads finished in 490 ms
  main: All threads started in 403 ms                                                         main: All threads started in 425 ms
  main: All threads finished in 280 ms                                                        main: All threads finished in 536 ms
  main: All threads started in 396 ms                                                         main: All threads started in 414 ms
  main: All threads finished in 240 ms                                                        main: All threads finished in 539 ms
  main: All threads started in 416 ms                                                         main: All threads started in 411 ms
  main: All threads finished in 248 ms                                                        main: All threads finished in 567 ms
  main: All threads started in 420 ms                                                         main: All threads started in 396 ms
  main: All threads finished in 289 ms                                                        main: All threads finished in 547 ms
  main: All threads started in 402 ms                                                         main: All threads started in 401 ms
  main: All threads finished in 245 ms                                                        main: All threads finished in 527 ms
  main: All threads started in 418 ms                                                         main: All threads started in 391 ms
  main: All threads finished in 314 ms                                                        main: All threads finished in 539 ms
  main: Shutdown in 1 ms                                                                      main: Shutdown in 0 ms

  Observations

    - Test with 100K vthreads is faster than with 30K pthreads
    - Slowdown of vthreads after GC
*/

public class TrivialVthreadMicroBenchmark {
    // Test Configuration
    public static boolean USE_VTHREADS = true;
    public static boolean SHOULD_TRIGGER_GC = false;
    public static int THREAD_CNT = 100_000;

    public static final long K = 1024;
    public static final long M = 1024 * K;

    public static final long NEW_GEN_SIZE_BYTES = 1024 * M;
    public static final int TMP_ARRAY_SIZE_BYTES = 10_000;
    public static final int REC_COUNT = 100;

    // Use java.util.concurrent synchronization to avoid pinning
    public static CountDownLatch threadsStartedLatch;
    public static CountDownLatch runToCompletionLatch;
    public static CountDownLatch threadsDoneLatch;

    public static ExecutorService executor;

    public static void main(String[] args) {
        // Process arguments
        if (args.length != 3) {
            log("Wrong number of arguments");
            log("Usage: <vthreads|pthreads> <gc|nogc> <thread cnt>");
            System.exit(1);
        }
        int i = 0;
        USE_VTHREADS = args[i++].equals("vthreads");
        SHOULD_TRIGGER_GC = args[i++].equals("gc");
        THREAD_CNT = Integer.parseInt(args[i++]);

        // Create an executor service according to the arguments
        executor = USE_VTHREADS ?
            Executors.newVirtualThreadPerTaskExecutor() :
            Executors.newFixedThreadPool(THREAD_CNT);
        // very slow w/o pooling
        //            Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory());

        // Run the test a few times
        IntStream.range(0, 10).forEach(j -> testEntry());
        long start = System.currentTimeMillis();
        executor.shutdown();
        long end = System.currentTimeMillis();
        log("Shutdown in " + (end - start) + " ms");
    }

    public static void testEntry() {
        threadsStartedLatch = new CountDownLatch(THREAD_CNT);
        runToCompletionLatch = new CountDownLatch(1);
        threadsDoneLatch = new CountDownLatch(THREAD_CNT);

        // Cleanup heap before test run
        System.gc();

        // Start threads
        long start = System.currentTimeMillis();
        IntStream.range(0, THREAD_CNT).forEach(i -> executor.submit(() -> threadEntry(i)));
        await(threadsStartedLatch);
        long end = System.currentTimeMillis();
        log("All threads started in " + (end - start) + " ms");

        if (SHOULD_TRIGGER_GC) {
            triggerGC();
        }

        // Let threads run to completion
        start = System.currentTimeMillis();
        runToCompletionLatch.countDown();
        await(threadsDoneLatch);
        end = System.currentTimeMillis();
        log("All threads finished in " + (end - start) + " ms");
    }

    public static int threadEntry(int i) {
        threadsStartedLatch.countDown();
        recMethod_dontinline(REC_COUNT);
        threadsDoneLatch.countDown();
        return i;
    }

    public static volatile byte[] tmpArray;
    public static void triggerGC() {
        long bytesToAllocate = SHOULD_TRIGGER_GC ? (NEW_GEN_SIZE_BYTES) : 0;
        while (bytesToAllocate > 0) {
            tmpArray = new byte[TMP_ARRAY_SIZE_BYTES];
            bytesToAllocate -= TMP_ARRAY_SIZE_BYTES;
        }
        tmpArray = null;
    }

    public static volatile long dummy = 0;
    public static void recMethod_dontinline(int i) {
        dummy++;
        if (i > 0) {
            recMethod_dontinline(i - 1);
            return;
        }
        await(runToCompletionLatch);
    }

    static void log(String m) {
        System.out.println(Thread.currentThread().getName() + ": " + m);
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }
}
