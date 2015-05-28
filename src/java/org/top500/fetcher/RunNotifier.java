package org.top500.fetcher;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class RunNotifier {
    private final List<RunListener> fListeners = Collections.synchronizedList(new ArrayList<RunListener>());

    public void addListener(RunListener listener) {
        fListeners.add(listener);
    }
    public void removeListener(RunListener listener) {
        fListeners.remove(listener);
    }

    private abstract class SafeNotifier {
        private final List<RunListener> fCurrentListeners;

        SafeNotifier() {
            this(fListeners);
        }

        SafeNotifier(List<RunListener> currentListeners) {
            fCurrentListeners = currentListeners;
        }

        void run() {
            synchronized (fListeners) {
                for (Iterator<RunListener> all = fCurrentListeners.iterator(); all.hasNext(); ) {
                    try {
                        RunListener listener = all.next();
                        notifyListener(listener);
                    } catch (Exception e) {
                    }
                }
            }
        }

        abstract protected void notifyListener(RunListener each) throws Exception;
    }

    public void fireStarted(final Object o) {
        new SafeNotifier() {
            @Override
            protected void notifyListener(RunListener each) throws Exception {
                each.Started(o);
            }
            ;
        }.run();
    }

    public void fireFinished(final Object o) {
        new SafeNotifier() {
            @Override
            protected void notifyListener(RunListener each) throws Exception {
                each.Finished(o);
            }
            ;
        }.run();
    }
}