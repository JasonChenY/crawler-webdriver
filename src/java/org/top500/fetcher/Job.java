package org.top500.fetcher;

import java.util.Map;
import java.util.HashMap;

public class Job {

    public static final String JOB_URL = "url";
    public static final String JOB_TITLE = "job_title";
    public static final String JOB_LOCATION = "job_location";
    public static final String JOB_DATE = "job_date";
    public static final String JOB_DESCRIPTION = "job_description";

    Map<String, String> fields;

    public Job() {
        fields = new HashMap<String, String>();
    }

    public void addField(String key, String value) {
        fields.put(key, value);
    }

    public String getField(String key) {
        if (fields.containsKey(key)) {
            return fields.get(key);
        } else {
            return null;
        }
    }
}