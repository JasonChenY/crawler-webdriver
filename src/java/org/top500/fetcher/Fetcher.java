package org.top500.fetcher;

import org.top500.schema.*;

import java.lang.System;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.top500.indexer.Indexer;
import org.top500.utils.Configuration;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class Fetcher extends RunListener {
    public static final Logger LOG = LoggerFactory.getLogger(Fetcher.class);
    private final List<Schema> _schemas;
    private final Configuration _conf;
    private PrintWriter _pw;

    public Fetcher(List<Schema> schemas, Configuration conf) {
        _conf = conf;
        _schemas = schemas;
    }

    public void fetch_all() throws Exception {
        final List<FetcherThread> threads = new ArrayList<FetcherThread>(_schemas.size());
        Throwable t = null;
        int i = 1;
        for (Schema schema : _schemas) {
            final FetcherThread thread = new FetcherThread(schema.getName()+"#" + i, schema);
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
        WebDriverService.CreateAndStartService(_conf.getInt("fetch.webdriver.port", 8899));

        _pw = new PrintWriter("/tmp/fetchstatus.data");

        FetcherPool fetcherPool = new FetcherPool(_conf.getInt("fetch.thread.size", 3), new Runnable() {
            public void run() {
                //System.out.println("General callback");
            }
        });

        int i = 0;
        for (Schema schema : _schemas) {
            final FetcherThread thread = new FetcherThread(schema.getName()+"#" + i, schema);
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

        _pw.close();

        WebDriverService.StopService();
    }

    public void Started(Object o) {}

    public void Finished(Object o) {
        FetcherThread thread = (FetcherThread)o;
        LOG.debug("Fetcher get Finished for " + thread.getName() + " result: " + (thread._schema.fetch_result?"COMPLETE":"PARTLY"));
        _pw.printf("%25s   %5b    %10s    %4d    %4d\n",
                    thread._schema.getName(),
                    thread._schema.fetch_result,
                    thread._schema.getFetchRuntimeIndex(),
                    thread._schema.fetch_total_jobs,
                    thread.getJoblist().count());
        _pw.flush();
    }

    public static void main(String[] args) {
        if ( args.length < 1 ) {
            LOG.warn("Usage: Fetcher <seedfile>");
            System.exit(0);
        }

        //Prepare schemas from seedfile
        List<Schema> schemas = new ArrayList<Schema>();
        try
        {
            InputStreamReader in=new InputStreamReader(new FileInputStream(args[0]));
            BufferedReader br=new BufferedReader(in);
            String line;
            while ((line = br.readLine()) != null) {
                if ( line.startsWith("#")) continue;
                try {
                    java.util.Scanner scan = new java.util.Scanner(line);
                    String company = scan.next();
                    String resumeIdxstr = "";
                    int resumeTotalJob = 0;
                    try {
                        Boolean result = scan.nextBoolean();
                        if ( result ) {
                            LOG.info(company + " fetched completely last time");
                            continue;
                        } else {
                            resumeIdxstr = scan.next();
                            resumeTotalJob = scan.nextInt();
                            LOG.info(company + " fetched to " + resumeIdxstr + ", last time total " + resumeTotalJob);
                        }
                    } catch ( java.util.NoSuchElementException ee ) {
                    }
                    Schema schema = new Schema(company+".json");
                    schema.fetch_result = true;
                    schema.setFetchRuntimeIndex(resumeIdxstr);
                    schema.fetch_total_jobs = resumeTotalJob;
                    schema.print();
                    schemas.add(schema);
                } catch ( Exception e ) {
                    LOG.warn("Failed to decode schema for " + line, e);
                }
            }
        } catch(Exception e){

        }

        //Start Indexer
        Configuration conf = Configuration.getInstance();
        Indexer indexer = new Indexer();
        boolean ret = indexer.Start(conf);
        if ( !ret ) {
            LOG.warn("Check indexer conf, abort!");
            System.exit(0);
        }

        //Start Fetcher
        try {
            Fetcher fetcher = new Fetcher(schemas, conf);
            RunNotifier notifier = new RunNotifier();
            notifier.addListener(indexer);
            notifier.addListener(fetcher);
            fetcher.fetch_in_batch(notifier);
        } catch ( Exception e ) {
            LOG.warn("Exception happen", e);
        } finally {
            indexer.Stop();
        }
    }
}
