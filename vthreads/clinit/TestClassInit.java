import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * The test provokes a situation where manny threads are blocked waiting for
 * class initialization to complete. The thread doing the initialization is also
 * blocked waiting for a the action of yet another thread. This situation can
 * lead to a deadlock if the threads are pinned virtual threads and there are
 * not enough carrier threads for that last thread to run and perform the action
 * the others are waiting for.
 *
 * There following enhancement prevents deadlocks of this type in most situations.
 *
 * 8369238: Allow virtual thread preemption on some common class initialization paths
 * https://bugs.openjdk.org/browse/JDK-8369238
 * Since: 26
 *
 * So jdk 25 is expected to deadlock running the test as show below while jdk 26
 * is expected not to.
 *
 * java -Duse_vthreads=true TestClassInit.java
 *
 * Maybe increase CONC_REQUESTS below if your system has many CPUs.
 */
public class TestClassInit {

    // Maybe increase this value if your system has many CPUs
    private static final int CONC_REQUESTS = 100;
    private static final Semaphore requSem = new Semaphore(CONC_REQUESTS);
    private static final PrintStream OUT = System.out;

    public static final ThreadFactory THREAD_FACTORY =
            Boolean.getBoolean("use_vthreads") ? Thread.ofVirtual().factory() : Thread.ofPlatform().factory();


    /** MAIN ***********************************************************************/
    public static void main(String[] args) {
        doConcurrently(() -> DBConnection.initialize("database"));
        while (true) {
            doConcurrently(() -> {
// ISSUE: many blocked/pinned waiting for class init to complete
                ServiceEndpoint.getRequest().handle();
            });
        }
    }

    private static void doConcurrently(Runnable task) {
        requSem.acquireUninterruptibly();
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } finally {
                requSem.release();
            }
        };
        THREAD_FACTORY.newThread(wrappedTask).start();
    }

    /** ServiceEndpoint *************************************************************/
    public static class ServiceEndpoint {
        static {
// ISSUE: waiting pinned for DBConnection initialization by unmounted thread
            DBConnection.waitForInitialization();
        }

        public static Request getRequest() {
            return new Request();
        }
    }

    /** Request ++++++**************************************************************/
    public static class Request {
        public void handle() {
            String data = DBConnection.query("SELECT * FROM table");
            OUT.print(data);
        }
    }

    /** DBConnection *+************************************************************/
    private static class DBConnection {
        private static CountDownLatch latch = new CountDownLatch(1);

        public static void initialize(String dbName) {
            OUT.println("Initializing DBConnection to " + dbName + ".");
            sleep(2000); // Simulate time-consuming initialization
            latch.countDown();
        }

        public static void waitForInitialization() {
            try {
                while(!latch.await(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    OUT.println("Waiting for DBConnection.");
                }
                OUT.println("DBConnection is READY!");
            } catch (InterruptedException e) { /* Ignored */ }
        }

        public static String query(String sql) {
            sleep(100); // Simulate request handling
            return "R";
        }
    }

    static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) { /* Ignored */ }
    }
}
