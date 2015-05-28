package org.top500.fetcher;

import org.top500.schema.*;
import java.util.ArrayList;
import java.util.List;

public class Fetcher extends RunListener {
    private final List<Schema> _schemas;

    public Fetcher(List<Schema> schemas) {
        _schemas = schemas;
    }

    public void fetch_all() throws Exception {
        final List<FetcherThread> threads = new ArrayList<FetcherThread>(_schemas.size());
        Throwable t = null;
        int i = 1;
        for (Schema schema : _schemas) {
            final FetcherThread thread = new FetcherThread("FetcherThread #" + i, schema);
            ++i;
            threads.add(thread);
            thread.start();
        }
        for (FetcherThread thread : threads) {
            try {
                thread.join();
                if (t == null) {
                    t = thread.getThrowable();
                } else {
                    final Throwable t2 = thread.getThrowable();
                    if (t2 != null) {
                        System.err.println(thread + " failed.");
                        t2.printStackTrace(System.err);
                    }
                }
            } catch (InterruptedException ignored) {
                interrupt(threads);
            }
        }
        if (t != null) {
            if (t instanceof Exception) {
                throw (Exception) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new RuntimeException("Unexpected Throwable " + t.getClass().getName(), t);
            }
        }
    }

    private void interrupt(List<FetcherThread> threads) {
        for (FetcherThread thread : threads) {
            thread.interrupt();
        }
    }

    //////////////////////////////////////Run in Batch Mode///////////////////////////////
    public void fetch_in_batch(final RunNotifier notifier) throws Exception {
        FetcherPool fetcherPool = new FetcherPool(3, new Runnable() {
            public void run() {
                System.out.println("General callback");
            }
        });

        int i = 0;
        for (Schema schema : _schemas) {
            final FetcherThread thread = new FetcherThread("FetcherThread #" + i, schema);
            ++i;
            fetcherPool.execute(new Runnable() {
                @Override
                public void run() {
                    thread.run(notifier);
                }
            });
        }
        try {
            fetcherPool.shutdownAndWait();
        } catch (InterruptedException e) {
            throw e;
        }
    }

    public void Started(Object o) {
        FetcherThread thread = (FetcherThread)o;
        System.out.println("RunListener " + thread.getName() + " started");
    }

    public void Finished(Object o) {
        FetcherThread thread = (FetcherThread)o;
        System.out.println("RunListener " + thread.getName() + " finsihed");
    }

    public static void main(String[] args) {
        try {
            List<Schema> schemas = new ArrayList<Schema>();
            schemas.add(new Schema("schema.template"));
            schemas.add(new Schema("schema2"));
            schemas.add(new Schema("schema3"));
            schemas.add(new Schema("schema4"));
            schemas.add(new Schema("schema5"));
            schemas.add(new Schema("schema6"));
            Fetcher fetcher = new Fetcher(schemas);
            fetcher.fetch_all();

            System.out.println("--------ThreadPool Test------------");
            RunNotifier notifer = new RunNotifier();
            notifer.addListener(fetcher);
            fetcher.fetch_in_batch(notifer);
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}
