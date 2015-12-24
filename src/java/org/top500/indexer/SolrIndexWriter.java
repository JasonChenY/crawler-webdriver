package org.top500.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.lang.StringBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.top500.fetcher.Job;
import org.top500.fetcher.Joblist;
import org.top500.utils.DateUtils;
import org.top500.utils.StringUtils;
import org.top500.utils.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.SolrQuery;

public class SolrIndexWriter {

    public static final Logger LOG = LoggerFactory.getLogger(SolrIndexWriter.class);

    private HttpSolrServer solr;
    private SolrMappingReader solrMapping;

    private Configuration config;

    private final List<SolrInputDocument> inputDocs = new ArrayList<SolrInputDocument>();

    private int batchSize;
    private int numDeletes = 0;
    private boolean delete = false;

    protected static long documentCount = 0;

    private static SolrIndexWriter instance = null;
    public static SolrIndexWriter getInstance(Configuration conf) throws Exception {
        if ( instance == null ) {
            instance = new SolrIndexWriter(conf);
        }
        return instance;
    }
    public static SolrIndexWriter getInstance() {
        return instance;
    }

    private SolrIndexWriter(Configuration conf) throws Exception {
        solr = SolrUtils.getHttpSolrServer(conf);
        batchSize = conf.getInt(SolrConstants.COMMIT_SIZE, 1000);
        solrMapping = SolrMappingReader.getInstance(conf);
    }

    public void write(Job job) throws IOException {
        final SolrInputDocument inputDoc = new SolrInputDocument();
        String url = "";
        String title = "";
        for(String key : job.getFields().keySet()) {
            Object val = job.getField(key);
/*
            if (key.equals(Job.JOB_DESCRIPTION)) {
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    md.update(val.getBytes());
                    String digest = StringUtils.toHexString(md.digest());
                    inputDoc.addField("digest", digest);
                } catch (NoSuchAlgorithmException e) {
                    LOG.warn("failed to get MD5 algo");
                }
            }
*/

            inputDoc.addField(solrMapping.mapKey(key), val);
            String sCopy = solrMapping.mapCopyKey(key);
            if (sCopy != key) {
                inputDoc.addField(sCopy, val);
            }
        }

        inputDoc.setDocumentBoost(0);
        inputDocs.add(inputDoc);
        documentCount++;
        if (inputDocs.size() >= batchSize) {
            try {
                LOG.info("Adding " + Integer.toString(inputDocs.size()) + " documents");
                solr.add(inputDocs);
                //if ( (documentCount%100) == 0 ) commit();
            } catch (final SolrServerException e) {
                throw new IOException(e);
            }
            inputDocs.clear();
        }
    }

    public void close() throws IOException {
        try {
            if (!inputDocs.isEmpty()) {
                LOG.info("Adding " + Integer.toString(inputDocs.size()) + " documents");
                solr.add(inputDocs);
                inputDocs.clear();
            } else if (numDeletes > 0) {
                LOG.info("Deleted " + Integer.toString(numDeletes) + " documents");
            }
        } catch (final SolrServerException e) {
            throw new IOException(e);
        }
    }


    public void delete(String key) throws IOException {
        if (delete) {
            try {
                solr.deleteById(key);
                numDeletes++;
            } catch (final SolrServerException e) {
                throw makeIOException(e);
            }
        }
    }

    public void update(Job job) throws IOException {
        final SolrInputDocument inputDoc = new SolrInputDocument();
        HashMap<String, Object> oper = new HashMap<String, Object>();
        oper.put("set", true);

        String key = Job.JOB_UNIQUE_ID;
        Object val = job.getField(key);
        inputDoc.addField(solrMapping.mapKey(key), val);
        String sCopy = solrMapping.mapCopyKey(key);
        if (sCopy != key) {
            inputDoc.addField(sCopy, val);
        }

        key = Job.JOB_EXPIRED;
        inputDoc.addField(key, oper);

        inputDocs.add(inputDoc);
        documentCount++;
        if (inputDocs.size() >= batchSize) {
            try {
                LOG.info("Adding " + Integer.toString(inputDocs.size()) + " documents");
                solr.add(inputDocs);
                //if ( (documentCount%100) == 0 ) commit();
            } catch (final SolrServerException e) {
                throw new IOException(e);
            }
            inputDocs.clear();
        }
    }

    public void commit() throws IOException {
        try {
            solr.commit();
            LOG.info("Total " + documentCount
                    + (documentCount > 1 ? " documents are " : " document is ")
                    + "added.");
        } catch (SolrServerException e) {
            throw makeIOException(e);
        }
    }

    public SolrDocument getById(String id) throws SolrServerException, IOException {
        return solr.getById(null, id, null);
    }

    public Joblist query(SolrQuery query) {
        LOG.debug("reach query");
        Joblist jobs = new Joblist();
        try {
            QueryResponse rsp = solr.query(query);
            SolrDocumentList hits = rsp.getResults();

            for (SolrDocument doc : hits) {
                Job job = new Job();
                for (String fieldName : doc.getFieldNames()) {
                    job.addField(fieldName, doc.getFieldValue(fieldName));
                }
                jobs.addJob(job);
            }
        } catch (Exception e ) {
            LOG.warn("Failed to get jobs with SolrQuery", e);
        }
        return jobs;
    }

    public static IOException makeIOException(SolrServerException e) {
        final IOException ioe = new IOException();
        ioe.initCause(e);
        return ioe;
    }

    public String describe() {
        StringBuffer sb = new StringBuffer("SOLRIndexWriter\n");
        sb.append("\t").append(SolrConstants.SERVER_URL)
                .append(" : URL of the SOLR instance (mandatory)\n");
        sb.append("\t").append(SolrConstants.COMMIT_SIZE)
                .append(" : buffer size when sending to SOLR (default 1000)\n");
        sb.append("\t")
                .append(SolrConstants.MAPPING_FILE)
                .append(
                        " : name of the mapping file for fields (default solrindex-mapping.xml)\n");
        sb.append("\t").append(SolrConstants.USE_AUTH)
                .append(" : use authentication (default false)\n");
        sb.append("\t").append(SolrConstants.USERNAME)
                .append(" : username for authentication\n");
        sb.append("\t").append(SolrConstants.PASSWORD)
                .append(" : password for authentication\n");
        return sb.toString();
    }
}
