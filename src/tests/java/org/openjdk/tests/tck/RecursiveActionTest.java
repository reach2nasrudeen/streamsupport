/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package org.openjdk.tests.tck;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import java8.util.concurrent.ForkJoinPool;
import java8.util.concurrent.ForkJoinTask;
import java8.util.concurrent.ForkJoinWorkerThread;
import java8.util.concurrent.RecursiveAction;

import java.util.concurrent.SynchronousQueue;

import java8.util.concurrent.ThreadLocalRandom;

import java.util.concurrent.TimeoutException;

import junit.framework.Test;
import junit.framework.TestSuite;

@org.testng.annotations.Test
public class RecursiveActionTest extends JSR166TestCase {
// CVS rev. 1.53

//    public static void main(String[] args) {
//        main(suite(), args);
//    }

    public static Test suite() {
        return new TestSuite(RecursiveActionTest.class);
    }

    private static ForkJoinPool mainPool() {
        return new ForkJoinPool();
    }

    private static ForkJoinPool singletonPool() {
        return new ForkJoinPool(1);
    }

    private static ForkJoinPool asyncSingletonPool() {
        return new ForkJoinPool(1,
                                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                                null, true);
    }

    private void testInvokeOnPool(ForkJoinPool pool, RecursiveAction a) {
        PoolCleaner cleaner = null;
        try {
            cleaner = cleaner(pool);

            checkNotDone(a);

            assertNull(pool.invoke(a));

            checkCompletedNormally(a);
        } finally {
            if (cleaner != null) {
                cleaner.close();
            }
        }
    }

    void checkNotDone(RecursiveAction a) {
        assertFalse(a.isDone());
        assertFalse(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertNull(a.getException());
        assertNull(a.getRawResult());

        if (! ForkJoinTask.inForkJoinPool()) {
            Thread.currentThread().interrupt();
            try {
                a.get();
                shouldThrow();
            } catch (InterruptedException success) {
            } catch (Throwable fail) { threadUnexpectedException(fail); }

            Thread.currentThread().interrupt();
            try {
                a.get(randomTimeout(), randomTimeUnit());
                shouldThrow();
            } catch (InterruptedException success) {
            } catch (Throwable fail) { threadUnexpectedException(fail); }
        }

        try {
            a.get(randomExpiredTimeout(), randomTimeUnit());
            shouldThrow();
        } catch (TimeoutException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCompletedNormally(RecursiveAction a) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertTrue(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertNull(a.getException());
        assertNull(a.getRawResult());
        assertNull(a.join());
        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));
        try {
            assertNull(a.get());
            assertNull(a.get(randomTimeout(), randomTimeUnit()));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCancelled(RecursiveAction a) {
        assertTrue(a.isDone());
        assertTrue(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertTrue(a.getException() instanceof CancellationException);
        assertNull(a.getRawResult());

        try {
            a.join();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(randomTimeout(), randomTimeUnit());
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCompletedAbnormally(RecursiveAction a, Throwable t) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertSame(t.getClass(), a.getException().getClass());
        assertNull(a.getRawResult());
        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));

        try {
            a.join();
            shouldThrow();
        } catch (Throwable expected) {
            assertSame(expected.getClass(), t.getClass());
        }

        try {
            a.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(randomTimeout(), randomTimeUnit());
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    @SuppressWarnings("serial")
    public static final class FJException extends RuntimeException {
        public FJException() { super(); }
        public FJException(Throwable cause) { super(cause); }
    }

    // A simple recursive action for testing
    final class FibAction extends CheckedRecursiveAction {
        private static final long serialVersionUID = 1L;
        final int number;
        int result;
        FibAction(int n) { number = n; }
        protected void realCompute() {
            int n = number;
            if (n <= 1)
                result = n;
            else {
                FibAction f1 = new FibAction(n - 1);
                FibAction f2 = new FibAction(n - 2);
                invokeAll(f1, f2);
                result = f1.result + f2.result;
            }
        }
    }

    // A recursive action failing in base case
    static final class FailingFibAction extends RecursiveAction {
        private static final long serialVersionUID = 1L;
        final int number;
        int result;
        FailingFibAction(int n) { number = n; }
        public void compute() {
            int n = number;
            if (n <= 1)
                throw new FJException();
            else {
                FailingFibAction f1 = new FailingFibAction(n - 1);
                FailingFibAction f2 = new FailingFibAction(n - 2);
                invokeAll(f1, f2);
                result = f1.result + f2.result;
            }
        }
    }

    /**
     * invoke returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks. getRawResult of a RecursiveAction returns null;
     */
    public void testInvoke() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertNull(f.invoke());
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                f.quietlyInvoke();
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertNull(f.join());
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join/quietlyJoin of a forked task succeeds in the presence of interrupts
     */
    public void testJoinIgnoresInterrupts() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                final Thread myself = Thread.currentThread();

                // test join()
                assertSame(f, f.fork());
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                assertNull(f.join());
                Thread.interrupted();
                assertEquals(21, f.result);
                checkCompletedNormally(f);

                f = new FibAction(8);
                f.cancel(true);
                assertSame(f, f.fork());
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                    Thread.interrupted();
                    checkCancelled(f);
                }

                f = new FibAction(8);
                f.completeExceptionally(new FJException());
                assertSame(f, f.fork());
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                    Thread.interrupted();
                    checkCompletedAbnormally(f, success);
                }

                // test quietlyJoin()
                f = new FibAction(8);
                assertSame(f, f.fork());
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                f.quietlyJoin();
                Thread.interrupted();
                assertEquals(21, f.result);
                checkCompletedNormally(f);

                f = new FibAction(8);
                f.cancel(true);
                assertSame(f, f.fork());
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                f.quietlyJoin();
                Thread.interrupted();
                checkCancelled(f);

                f = new FibAction(8);
                f.completeExceptionally(new FJException());
                assertSame(f, f.fork());
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                f.quietlyJoin();
                Thread.interrupted();
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(mainPool(), a);
        a.reinitialize();
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * join/quietlyJoin of a forked task when not in ForkJoinPool
     * succeeds in the presence of interrupts
     */
    public void testJoinIgnoresInterruptsOutsideForkJoinPool() {
        final SynchronousQueue<FibAction[]> sq =
            new SynchronousQueue<>();
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws InterruptedException {
                FibAction[] fibActions = new FibAction[6];
                for (int i = 0; i < fibActions.length; i++)
                    fibActions[i] = new FibAction(8);

                fibActions[1].cancel(false);
                fibActions[2].completeExceptionally(new FJException());
                fibActions[4].cancel(true);
                fibActions[5].completeExceptionally(new FJException());

                for (int i = 0; i < fibActions.length; i++)
                    fibActions[i].fork();

                sq.put(fibActions);

                helpQuiesce();
            }};

        Runnable r = new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                FibAction[] fibActions = sq.take();
                FibAction f;
                final Thread myself = Thread.currentThread();

                // test join() ------------

                f = fibActions[0];
                assertFalse(ForkJoinTask.inForkJoinPool());
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                assertNull(f.join());
                assertTrue(Thread.interrupted());
                assertEquals(21, f.result);
                checkCompletedNormally(f);

                f = fibActions[1];
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                    assertTrue(Thread.interrupted());
                    checkCancelled(f);
                }

                f = fibActions[2];
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                    assertTrue(Thread.interrupted());
                    checkCompletedAbnormally(f, success);
                }

                // test quietlyJoin() ---------

                f = fibActions[3];
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                f.quietlyJoin();
                assertTrue(Thread.interrupted());
                assertEquals(21, f.result);
                checkCompletedNormally(f);

                f = fibActions[4];
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                f.quietlyJoin();
                assertTrue(Thread.interrupted());
                checkCancelled(f);

                f = fibActions[5];
                myself.interrupt();
                assertTrue(myself.isInterrupted());
                f.quietlyJoin();
                assertTrue(Thread.interrupted());
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};

        Thread t;

        t = newStartedThread(r);
        testInvokeOnPool(mainPool(), a);
        awaitTermination(t);

        a.reinitialize();
        t = newStartedThread(r);
        testInvokeOnPool(singletonPool(), a);
        awaitTermination(t);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGet() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertNull(f.get());
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGet() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertNull(f.get(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPE() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                try {
                    f.get(randomTimeout(), null);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoin() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertEquals(21, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesce() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                helpQuiesce();
                while (!f.isDone()) // wait out race
                    ;
                assertEquals(21, f.result);
                assertEquals(0, getQueuedTaskCount());
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvoke() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvoke() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                f.quietlyInvoke();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoin() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGet() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGet() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoin() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvoke() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                try {
                    f.invoke();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoin() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGet() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGet() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoin() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                f.quietlyJoin();
                checkCancelled(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * getPool of executing task returns its pool
     */
    public void testGetPool() {
        final ForkJoinPool mainPool = mainPool();
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertSame(mainPool, getPool());
            }};
        testInvokeOnPool(mainPool, a);
    }

    /**
     * getPool of non-FJ task returns null
     */
    public void testGetPool2() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertNull(getPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * inForkJoinPool of executing task returns true
     */
    public void testInForkJoinPool() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertTrue(inForkJoinPool());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * inForkJoinPool of non-FJ task returns false
     */
    public void testInForkJoinPool2() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                assertFalse(inForkJoinPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * getPool of current thread in pool returns its pool
     */
    public void testWorkerGetPool() {
        final ForkJoinPool mainPool = mainPool();
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                ForkJoinWorkerThread w =
                    (ForkJoinWorkerThread) Thread.currentThread();
                assertSame(mainPool, w.getPool());
            }};
        testInvokeOnPool(mainPool, a);
    }

    /**
     * getPoolIndex of current thread in pool returns 0 <= value < poolSize
     */
    public void testWorkerGetPoolIndex() {
        final ForkJoinPool mainPool = mainPool();
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                ForkJoinWorkerThread w =
                    (ForkJoinWorkerThread) Thread.currentThread();
                assertTrue(w.getPoolIndex() >= 0);
                // pool size can shrink after assigning index, so cannot check
                // assertTrue(w.getPoolIndex() < mainPool.getPoolSize());
            }};
        testInvokeOnPool(mainPool, a);
    }

    /**
     * setRawResult(null) succeeds
     */
    public void testSetRawResult() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                setRawResult(null);
                assertNull(getRawResult());
            }};
        assertNull(a.invoke());
    }

    /**
     * A reinitialized normally completed task may be re-invoked
     */
    public void testReinitialize() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                checkNotDone(f);

                for (int i = 0; i < 3; i++) {
                    assertNull(f.invoke());
                    assertEquals(21, f.result);
                    checkCompletedNormally(f);
                    f.reinitialize();
                    checkNotDone(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * A reinitialized abnormally completed task may be re-invoked
     */
    public void testReinitializeAbnormal() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                checkNotDone(f);

                for (int i = 0; i < 3; i++) {
                    try {
                        f.invoke();
                        shouldThrow();
                    } catch (FJException success) {
                        checkCompletedAbnormally(f, success);
                    }
                    f.reinitialize();
                    checkNotDone(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionally() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                f.completeExceptionally(new FJException());
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task suppresses execution invoking complete
     */
    public void testComplete() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                f.complete(null);
                assertNull(f.invoke());
                assertEquals(0, f.result);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                invokeAll(f, g);
                checkCompletedNormally(f);
                assertEquals(21, f.result);
                checkCompletedNormally(g);
                assertEquals(34, g.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                invokeAll(f);
                checkCompletedNormally(f);
                assertEquals(21, f.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                invokeAll(f, g, h);
                assertTrue(f.isDone());
                assertTrue(g.isDone());
                assertTrue(h.isDone());
                checkCompletedNormally(f);
                assertEquals(21, f.result);
                checkCompletedNormally(g);
                assertEquals(34, g.result);
                checkCompletedNormally(g);
                assertEquals(13, h.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollection() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                HashSet<FibAction> set = new HashSet<>();
                set.add(f);
                set.add(g);
                set.add(h);
                invokeAll(set);
                assertTrue(f.isDone());
                assertTrue(g.isDone());
                assertTrue(h.isDone());
                checkCompletedNormally(f);
                assertEquals(21, f.result);
                checkCompletedNormally(g);
                assertEquals(34, g.result);
                checkCompletedNormally(g);
                assertEquals(13, h.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPE() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = null;
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                FailingFibAction g = new FailingFibAction(9);
                try {
                    invokeAll(f, g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction g = new FailingFibAction(9);
                try {
                    invokeAll(g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction f = new FibAction(8);
                FailingFibAction g = new FailingFibAction(9);
                FibAction h = new FibAction(7);
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection) throws exception if any task does
     */
    public void testAbnormalInvokeAllCollection() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                HashSet<RecursiveAction> set = new HashSet<>();
                set.add(f);
                set.add(g);
                set.add(h);
                try {
                    invokeAll(set);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * tryUnfork returns true for most recent unexecuted task,
     * and suppresses execution
     */
    public void testTryUnfork() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertTrue(f.tryUnfork());
                helpQuiesce();
                checkNotDone(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * getSurplusQueuedTaskCount returns > 0 when
     * there are more tasks than threads
     */
    public void testGetSurplusQueuedTaskCount() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction h = new FibAction(7);
                assertSame(h, h.fork());
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertTrue(getSurplusQueuedTaskCount() > 0);
                helpQuiesce();
                assertEquals(0, getSurplusQueuedTaskCount());
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns most recent unexecuted task.
     */
    public void testPeekNextLocalTask() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(f, peekNextLocalTask());
                assertNull(f.join());
                checkCompletedNormally(f);
                helpQuiesce();
                checkCompletedNormally(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollNextLocalTask returns most recent unexecuted task
     * without executing it
     */
    public void testPollNextLocalTask() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(f, pollNextLocalTask());
                helpQuiesce();
                checkNotDone(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task without executing it
     */
    public void testPollTask() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(f, pollTask());
                helpQuiesce();
                checkNotDone(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns least recent unexecuted task in async mode
     */
    public void testPeekNextLocalTaskAsync() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(g, peekNextLocalTask());
                assertNull(f.join());
                helpQuiesce();
                checkCompletedNormally(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollNextLocalTask returns least recent unexecuted task without
     * executing it, in async mode
     */
    public void testPollNextLocalTaskAsync() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(g, pollNextLocalTask());
                helpQuiesce();
                checkCompletedNormally(f);
                checkNotDone(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task without executing it, in
     * async mode
     */
    public void testPollTaskAsync() {
        @SuppressWarnings("serial")
        RecursiveAction a = new CheckedRecursiveAction() {
            protected void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(g, pollTask());
                helpQuiesce();
                checkCompletedNormally(f);
                checkNotDone(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /** Demo from RecursiveAction javadoc */
    static class SortTask extends RecursiveAction {
        private static final long serialVersionUID = 1L;
        final long[] array; final int lo, hi;
        SortTask(long[] array, int lo, int hi) {
            this.array = array; this.lo = lo; this.hi = hi;
        }
        SortTask(long[] array) { this(array, 0, array.length); }
        protected void compute() {
            if (hi - lo < THRESHOLD)
                sortSequentially(lo, hi);
            else {
                int mid = (lo + hi) >>> 1;
                invokeAll(new SortTask(array, lo, mid),
                          new SortTask(array, mid, hi));
                merge(lo, mid, hi);
            }
        }
        // implementation details follow:
        static final int THRESHOLD = 100;
        void sortSequentially(int lo, int hi) {
            Arrays.sort(array, lo, hi);
        }
        void merge(int lo, int mid, int hi) {
            long[] buf = Arrays.copyOfRange(array, lo, mid);
            for (int i = 0, j = lo, k = mid; i < buf.length; j++)
                array[j] = (k == hi || buf[i] < array[k]) ?
                    buf[i++] : array[k++];
        }
    }

    /**
     * SortTask demo works as advertised
     */
    public void testSortTaskDemo() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long[] array = new long[1007];
        for (int i = 0; i < array.length; i++)
            array[i] = rnd.nextLong();
        long[] arrayClone = array.clone();
        testInvokeOnPool(mainPool(), new SortTask(array));
        Arrays.sort(arrayClone);
        assertTrue(Arrays.equals(array, arrayClone));
    }
}
