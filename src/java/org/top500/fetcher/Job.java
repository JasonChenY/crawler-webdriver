package org.top500.fetcher;

import java.util.Map;
import java.util.HashMap;

public class Job {
    public static final String JOB_UNIQUE_ID = "id";
    public static final String JOB_COMPANY = "job_company";
    public static final String JOB_SUB_COMPANY = "job_sub_company";
    public static final String JOB_URL = "job_url";
    public static final String JOB_URL_TYPE = "job_url_type";
    public static final String JOB_TYPE = "job_type";
    public static final String JOB_TITLE = "job_title";
    public static final String JOB_LOCATION = "job_location";
    public static final String JOB_POST_DATE = "job_post_date";
    public static final String JOB_EXPIRE_DATE = "job_expire_date";
    public static final String JOB_DESCRIPTION = "job_description";
    public static final String JOB_INDEX_DATE = "job_index_date";
    public static final String JOB_CATEGORY_DOMAIN = "job_category_domain";
    public static final String JOB_EXPIRED = "job_expired";

    final Map<String, Object> fields;
    boolean expiredViaDate = false;

    public Job() {
        fields = new HashMap<String, Object>();
        expiredViaDate = false;
    }

    public void addField(String key, Object value) {
        fields.put(key, value);
    }
    public void removeField(String key) {
	fields.remove(key);
    }
    public Object getField(String key) {
        if (fields.containsKey(key)) {
            return fields.get(key);
        } else {
            return null;
        }
    }

    public final Map<String, Object> getFields() {
        return fields;
    }

    public void setExpiredViaDate(boolean expired) { expiredViaDate = expired; }
    public boolean isExpiredViaDate() { return expiredViaDate; }
}
