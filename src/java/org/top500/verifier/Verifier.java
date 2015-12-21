package org.top500.verifier;

import org.top500.fetcher.RunListener;
import org.top500.schema.*;
import org.top500.fetcher.Job;
import org.top500.fetcher.Joblist;

import java.io.File;
import java.lang.System;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.top500.indexer.Indexer;
import org.top500.indexer.SolrIndexWriter;
import org.top500.utils.CompanyUtils;
import org.top500.utils.Configuration;
import org.top500.utils.DateUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.top500.fetcher.RunNotifier;

import org.top500.fetcher.FetcherPool;
import org.top500.fetcher.FetcherThread;
import org.top500.fetcher.WebDriverService;

public class Verifier extends RunListener {
    public static final Logger LOG = LoggerFactory.getLogger(Verifier.class);
    private final Map<String, Schema> schemas = new HashMap<String, Schema>();
    private Configuration conf = null;
    SolrQuery query = null;
    private final Object lock = new Object();
    int runningFetcherThread = 0;
    int invalidated_total = 0;

    public Schema polishSchema(Schema schema) {
        // to use inner most procedure as the Procedure
        // use last 2nd outer procedure's last action as action
        Schema.Procedure parent = schema.procedures.get(schema.procedures.size()-1);
        Schema.Procedure child = (parent.procedures.size()>0)?parent.procedures.get(parent.procedures.size()-1):null;
        while ( (child!=null) && (child.loop_type!=Schema.LOOP_TYPE.NONE) ) {
            parent=child;
            child=(parent.procedures.size()>0)?parent.procedures.get(parent.procedures.size()-1):null;
        }
        if ( child==null ) {
            LOG.warn(schema.getName() + ":" + "schema without single job procedure cant be polished");
            return null;
        } else {
            if ((parent.actions != null) && (parent.actions.actions.size() > 0)) {
                Schema.Action action = parent.actions.actions.get(parent.actions.actions.size() - 1);
                // hack last action.
                action.preaction = null;
                if ( action.element == null ) {
                    action.initElement();
                }
                action.element.how = "url";

                action.command.code = Schema.CmdType.Load;

                // hack last actions' expection
                if ( action.expections != null ) {
                    for (int i = 0; i < action.expections.expections.size(); i++ ) {
                        if ( action.expections.expections.get(i).condition.equals("newWindowIsOpened")
                           ||action.expections.expections.get(i).condition.equals("newWindowIsOpenedBackground") ) {
                            action.expections.expections.remove(i);
                            break;
                        }
                    }
                }
                // hook new action into schema
                schema.actions.actions.clear();
                schema.actions.actions.add(action);
            } else {
                LOG.warn(schema.getName() + ":" + "last 2nd outer procedure dont have actions");
                return null;
            }

            // hack inner most procedure
            if ( child.actions != null ) {
                child.actions.actions.clear();
            }
            schema.procedures.clear();
            schema.procedures.add(child);

            // hack to use phantomjs always
            schema.driver = "phantomjs";

            // hack the schema type, FetcherThread will do some different handling.
            schema.schemaType = Schema.SchemaType.verify;
        }
        return schema;
    }
    private class FileNameSelector implements java.io.FilenameFilter {
        String extension = ".";
        public FileNameSelector(String fileExtensionNoDot) {
            extension += fileExtensionNoDot;
        }
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(extension);
        }
    }
    public void initSchemas(String dir) {
        File directory = new File(dir);
        File[] jsonFiles = directory.listFiles(new FileNameSelector("json"));
        for(File file : jsonFiles)
        {
            try {
                Schema schema = new Schema(file.getName());
                String displayName = CompanyUtils.getDisplayName(schema.getName());
                schema = polishSchema(schema);
                schemas.put(displayName, schema);
            } catch ( Exception e ) {
                LOG.warn("Failed to polish schema for " + file.getName(), e);
            }
        }
    }

    private void initQuery() {
        query = new SolrQuery("*:*");

        query.addSort(Job.JOB_POST_DATE, ORDER.asc);
        query.addField(Job.JOB_UNIQUE_ID);
        query.addField(Job.JOB_URL);
        query.addField(Job.JOB_COMPANY);
        query.addField(Job.JOB_EXPIRE_DATE);

        query.setStart(conf.getInt("verify.query.start", 0));
        query.setRows(conf.getInt("verify.query.rows", 200));

        query.addFilterQuery(Job.JOB_EXPIRED + ":false");
        query.addFilterQuery(Job.JOB_POST_DATE + ":[* TO " + DateUtils.getNDaysAgoDate(conf.getInt("verify.n.days", 15)) + "]");
    }
    public void setNextQuery() {
        query.setStart(query.getStart() + query.getRows() - invalidated_total);
        invalidated_total = 0;
    }
    public void setQueryCompany(String[] companies) {
        StringBuilder sb = new StringBuilder();
        sb.append(Job.JOB_COMPANY);
        sb.append(":(");
        boolean first = true;
        for ( int i = 0; i < companies.length; i++ ) {
            String displayName = CompanyUtils.getDisplayName(companies[i]);
            if (!displayName.equals("unknown")) {
                if ( first ) {
                    first = false;
                } else {
                    sb.append(" OR ");
                }
                sb.append("\"");
                sb.append(displayName);
                sb.append("\"");
            } else {
                LOG.warn("unknown QueryCompany" + companies[i]);
            }
        }
        sb.append(")");
        if ( !first ) {
            query.addFilterQuery(sb.toString());
            LOG.debug(sb.toString());
        }
    }

    public Verifier(String dir, Configuration conf) {
        this.conf = conf;
        initSchemas(dir);
        initQuery();
    }

    public void verify(final RunNotifier notifier) throws Exception {
        FetcherPool fetcherPool = new FetcherPool(conf.getInt("verify.thread.size", 1), new Runnable() {
            public void run() {}
        });

        while ( true ) {
            Joblist joblist = null;
            try {
                joblist = SolrIndexWriter.getInstance(conf).query(query);
            } catch ( Exception e ) {
                LOG.warn("Verifying failed to get jobs", e);
                break;
            }
            if ( joblist.count() == 0 ) {
                LOG.info("Verifying finished");
                break;
            } else {
                LOG.info("Verifying (start:" + query.getStart() + ") jobs");
            }

            Iterator<Job> iter = joblist.getJobs().iterator();
            while(iter.hasNext()){
                Job job = iter.next();

                String companyName = (String)job.getField(Job.JOB_COMPANY);
                Schema schema = schemas.get(companyName);
                if ( schema == null ) {
                    LOG.warn("Unknow company:" + companyName + ", skipped");
                    iter.remove();
                    continue;
                }
                final FetcherThread thread = new FetcherThread(schema.getName(), schema);
                thread.getJoblist().addJob(job);
                iter.remove();

                while (iter.hasNext()) {
                    Job jobtmp = iter.next();

                    if ( ((String)jobtmp.getField(Job.JOB_COMPANY)).equals(companyName) ) {
                        thread.getJoblist().addJob(jobtmp);
                        iter.remove();
                    }
                }

                fetcherPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        thread.run(notifier);
                    }
                });
                runningFetcherThread++;
                iter = joblist.getJobs().iterator();
            }

            synchronized (lock) {
                lock.wait();
            }

            setNextQuery();
        }

        try {
            fetcherPool.shutdownAndWait();
        } catch (InterruptedException e) {
            throw e;
        }
    }

    public void Started(Object o) {}

    public void Finished(Object o) {
        FetcherThread thread = (FetcherThread)o;
        Joblist joblist = thread.getJoblist();
        int scanned = joblist.count();
        Iterator<Job> iter = joblist.getJobs().iterator();
        while(iter.hasNext()){
            Job job = iter.next();
            if ( job.getFields().containsKey(Job.JOB_EXPIRED)) {
                LOG.info("Invalidate job: " + (String)job.getField(Job.JOB_URL));
            } else {
                LOG.debug("Keep job: " + (String)job.getField(Job.JOB_URL));
                iter.remove();
            }
        }
        int invalidated = joblist.count();
        if ( invalidated == scanned ) {
            LOG.warn(thread.getName() + ": all jobs invalidated???, hold on cross check network");
            joblist.clear();
            invalidated = 0;
        }

        // consider delta in next batch's query.
        // problem new 'expired' job might not be synced to solr yet
        // if adjust the 'start-=invalidated_total' in query, there might be some duplicate entries,
        // but worst case(most jobs expired) at most 50 entries duplicated, because index will commit for every 50 jobs.
        // perfect solution is to query next batch until sync finished.
        invalidated_total += invalidated;
        runningFetcherThread--;

        if ( runningFetcherThread == 0 ) {
            synchronized (lock) {
                lock.notify();
            }
        }
    }
    public static void main(String[] args) {
        // Global Settings
        Configuration conf = Configuration.getInstance();

        //Start Indexer
        Indexer indexer = new Indexer();
        boolean ret = indexer.Start(conf);
        if ( !ret ) {
            LOG.warn("Check indexer conf, abort!");
            System.exit(0);
        }

        //Start Verifier
        try {
            Verifier verifier = new Verifier("conf", conf);
            if ( args.length >= 1 ) {
                LOG.debug("Verify jobs for specified companies");
                verifier.setQueryCompany(args);
            }

            RunNotifier notifier = new RunNotifier();
            notifier.addListener(verifier);
            notifier.addListener(indexer);

            WebDriverService.CreateAndStartService();

            verifier.verify(notifier);
        } catch ( Exception e ) {
            LOG.warn("Exception happen", e);
        } finally {
            indexer.Stop();
            WebDriverService.StopService();
        }
    }
}
