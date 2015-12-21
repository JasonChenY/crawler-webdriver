package org.top500.indexer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.lang.Thread;

import org.top500.utils.Configuration;
import org.top500.indexer.SolrConstants;
import org.top500.fetcher.FetcherThread;
import org.top500.fetcher.RunListener;
import org.top500.fetcher.Job;
import org.top500.fetcher.Joblist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Indexer extends RunListener {
    public static Logger LOG = LoggerFactory.getLogger(Indexer.class);
    private volatile boolean isStopped = false;
    Queue<Object> finish_queue = new LinkedList<Object>();
    SolrIndexWriter writer = null;
    private final Object lock = new Object();

    public void Started(Object o) {
        FetcherThread thread = (FetcherThread) o;
        LOG.debug("Indexer RunListener get Started for " + thread.getName());
    }

    public void Finished(Object o) {
        FetcherThread thread = (FetcherThread) o;
        LOG.debug("Indexer RunListener get Finished for " + thread.getName() + ",fetched " + thread.getJoblist().count() + " jobs");

        Joblist joblist = thread.getJoblist();
        for (int i = 0; i < joblist.count(); i++) {
            LOG.debug((String)joblist.get(i).getField(Job.JOB_URL));
        }

        synchronized (finish_queue){
            finish_queue.add(o);
        }
        synchronized (lock) {
            lock.notify();
        }
    }

    private class IndexerThread extends Thread {
        public void run() {
            Joblist joblist = new Joblist();
            while ( !isStopped ) {
                try {
                    synchronized (lock) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    LOG.debug("some fetcher thread finished?");
                }
                /* indexing */

                synchronized (finish_queue) {
                    while(!finish_queue.isEmpty()) {
                        FetcherThread fetcher = (FetcherThread) finish_queue.remove();
                        joblist.addAll(fetcher.getJoblist().getJobs());
                    }
                }

                /* Now to indexing in backgroupd */
                try {
                    for (Job job : joblist.getJobs()) {
                        if ( job.getFields().containsKey(Job.JOB_EXPIRED)
                                && ((boolean)job.getField(Job.JOB_EXPIRED)) ) {
                            // if job contains job_expired and set to true, should be an updating
                            writer.update(job);
                        } else {
                            writer.write(job);
                        }
                    }
                    joblist.clear();
                } catch ( Exception e ) {
                    LOG.warn("SolrIndexWriter fail to write");
                }
            }
            try {
                LOG.info("SolrIndexWriter close");
                writer.close();
                writer.commit();
            } catch ( Exception e) {
                LOG.warn("SolrIndexWriter close failed", e);
            }
        }
    }

    public boolean Start(Configuration conf) {
        try {
            writer = SolrIndexWriter.getInstance(conf);
        } catch ( Exception e ) {
            LOG.warn("Failed to create SolrIndexWriter");
            return false;
        }
        IndexerThread indexer = new IndexerThread();
        indexer.start();
        return true;
    }

    public void Stop() {
        LOG.warn("Graceful Stop Indexer");
        isStopped = true;
        synchronized (lock) {
            lock.notify();
        }
    }
    public SolrIndexWriter getWriter() {
        return writer;
    }
    public static void main(String[] args) {
        Configuration conf = Configuration.getInstance();
        Indexer indexer = new Indexer();
        boolean ret = indexer.Start(conf);
        if ( !ret ) {
            LOG.warn("Check indexer conf, abort!");
            System.exit(0);
        }

        for ( int i = 0; i < 1; i++) {
            String str = String.valueOf(i);
            Job job = new Job();
            job.addField(Job.JOB_COMPANY, "Test");
            job.addField(Job.JOB_URL, "http://a.b.c/" + str);
            job.addField(Job.JOB_TITLE, str);
            job.addField(Job.JOB_LOCATION, "Shanghai");
            job.addField(Job.JOB_POST_DATE, org.top500.utils.DateUtils.getCurrentDate());
            job.addField(Job.JOB_DESCRIPTION, str);
            job.addField(Job.JOB_INDEX_DATE, org.top500.utils.DateUtils.getCurrentDate());
            job.addField(Job.JOB_UNIQUE_ID, str); 
            
            try {
                indexer.getWriter().write(job);
            } catch ( Exception e ) {
                LOG.warn("Failed to write job");
            }
        }
        indexer.Stop();
    }
}
