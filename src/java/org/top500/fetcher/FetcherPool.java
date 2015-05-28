package org.top500.fetcher;

import org.top500.schema.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FetcherPool {
    private final List<Thread> threads;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
    private volatile boolean isStopped = false;

    public FetcherPool(int threadCount, final Runnable perThreadCleanup) {
      threads = new ArrayList<Thread>(threadCount);
      for (int i = 0; i < threadCount; ++i) {
        Thread thread = new Thread() {
          @Override
          public void run() {
            while (!isStopped || !tasks.isEmpty()) {
              Runnable task = tasks.poll();
              if (task != null) {
                task.run();
              }
              Thread.yield();
            }
            perThreadCleanup.run();
          }
        };

        thread.start();
        threads.add(thread);
      }
    }

    public void execute(Runnable runnable) {
      if ( isStopped ) {
          System.out.println("Thread pool has been shut down, not admitting new tasks");
      } else
         tasks.add(runnable);
    }

    public void shutdownAndWait() throws InterruptedException {
      isStopped = true;
      for (Thread thread : threads) {
        thread.join();
      }
    }
}


