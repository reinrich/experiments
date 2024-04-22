import java.util.concurrent.StructuredTaskScope;
import java.time.Duration;

/*
  java --enable-preview --source 21 StructuredConcurrencyForkFromParentInChild.java

  test_01 shows that it is possible to for a task in a parent scope.
  test_02 throws WrongThreadException when trying to close the parent scope in a subtask
  test_03 throws StructureViolationException
*/

public class StructuredConcurrencyForkFromParentInChild {

    public static void main(String[] args) {
        test_01();
    }


    //// test_01

    public static void test_01() {
        try {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure("ParentScope", Thread.ofVirtual().factory())) {
                var task1 = scope.fork(() -> computeWith(scope, false /* doClose */));
                var task2 = scope.fork(() -> computeWith(scope, false /* doClose */));

                scope.join()
                    .throwIfFailed();

                System.out.println("task1: " + task1.get());
                System.out.println("task2: " + task2.get());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    //// test_02

    public static void test_02() {
        try {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure("ParentScope", Thread.ofVirtual().factory())) {
                var task1 = scope.fork(() -> computeWith(scope, true /* doClose */));
                var task2 = scope.fork(() -> computeWith(scope, true /* doClose */));

                scope.join()
                    .throwIfFailed();

                System.out.println("task1: " + task1.get());
                System.out.println("task2: " + task2.get());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    //// test_03

    public static void test_03() {
        try {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure("ParentScope", Thread.ofVirtual().factory())) {
                computeWith(scope, true /* doClose */);
                scope.join().throwIfFailed();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    static int computeWith(StructuredTaskScope.ShutdownOnFailure parentScope, boolean doClose) {
        int result = -1;
        try {
            try (var childScope = new StructuredTaskScope.ShutdownOnFailure("ChildScope", Thread.ofVirtual().factory())) {
                var task3 = childScope.fork(() -> sleepThenReturn(Duration.ofSeconds(2), 3) );
                var task4 = parentScope.fork(() -> sleepThenReturn(Duration.ofSeconds(1), 4));

                childScope.join()
                    .throwIfFailed();

                if (doClose) {
                    parentScope.close();
                }

                System.out.println("task3: " + task3.get());
                System.out.println("task4: " + task4.get());
                result = task3.get() + task4.get();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return result;
    }

    static int sleepThenReturn(Duration d, int result) {
        try {
            Thread.sleep(d);
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }
}
