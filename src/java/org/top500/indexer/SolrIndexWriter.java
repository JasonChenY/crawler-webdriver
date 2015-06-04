package org.top500.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.lang.StringBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.top500.fetcher.Job;
import org.top500.utils.StringUtils;
import org.top500.utils.DateUtils;
import org.top500.utils.LocationUtils;
import org.top500.utils.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;

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

    public SolrIndexWriter(Configuration conf) throws Exception {
        solr = SolrUtils.getHttpSolrServer(conf);
        batchSize = conf.getInt(SolrConstants.COMMIT_SIZE, 1000);
        solrMapping = SolrMappingReader.getInstance(conf);
    }

    public void write(Job job) throws IOException {
        final SolrInputDocument inputDoc = new SolrInputDocument();

        for(String key : job.getFields().keySet()) {
            String val = job.getField(key);

            if (key.equals(Job.JOB_DESCRIPTION) || key.equals(Job.JOB_TITLE)) {
                val = StringUtils.stripNonCharCodepoints(val);
            }

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

            if (key.equals(Job.JOB_URL)) {
                inputDoc.addField("id", val+ String.valueOf(System.currentTimeMillis()));
            }

            inputDoc.addField(solrMapping.mapKey(key), val);
            String sCopy = solrMapping.mapCopyKey(key);
            if (sCopy != key) {
                inputDoc.addField(sCopy, val);
            }
        }
        if ( !job.getFields().containsKey(Job.JOB_DATE) ) {
            LOG.info("No Job_date field extracted, use current time");
            inputDoc.addField(Job.JOB_DATE, DateUtils.getCurrentDate());
        }

        // Some internal field

        inputDoc.addField("tstamp", DateUtils.getCurrentDate());
        inputDoc.addField("boost","1.0");

        inputDoc.setDocumentBoost(0);
        inputDocs.add(inputDoc);
        documentCount++;
        if (inputDocs.size() >= batchSize) {
            try {
                LOG.info("Adding " + Integer.toString(inputDocs.size()) + " documents");
                solr.add(inputDocs);
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
        write(job);
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
