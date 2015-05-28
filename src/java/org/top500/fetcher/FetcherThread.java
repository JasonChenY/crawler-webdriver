package org.top500.fetcher;

import org.top500.schema.*;

import java.lang.Override;
import java.lang.Thread;

public class FetcherThread extends Thread {
    private final Schema _schema;
    private volatile Throwable _throwable;
    private final Joblist _joblist = null;
    public FetcherThread(String name, Schema schema) {
        super(name);
        this._schema = schema;
    }
    public Throwable getThrowable() {
        return _throwable;
    }
    public Joblist getJoblist() {
        return _joblist;
    }

    public void run(RunNotifier notifier) {
        /* to be runned in threadpool mode, will notify the listener about progress */
        if ( notifier != null ) {
            notifier.fireStarted(this);
        } else {
            System.out.println(this.getName() + " for " + _schema.name + " start .... (no notifier)");
        }
        try {
            Thread.sleep(10);
        } catch (Throwable t) {
            _throwable = t;
        }
        if ( notifier != null ) {
            notifier.fireFinished(this);
        } else {
            System.out.println(this.getName() + " for " + _schema.name + " finish (no notifier)");
        }
    }

    @Override
    public void run() {
        //this is the original JDK thread
        System.out.println("Not in Threadpool mode");
        run(null);
    }
}