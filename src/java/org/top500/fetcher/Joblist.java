package org.top500.fetcher;

import java.util.List;
import java.util.ArrayList;

public class Joblist {
    List<Job> jobs;
    public Joblist() {
        jobs = new ArrayList<Job>();
    }
    public void addJob(Job job) {
        jobs.add(job);
    }
    public int count() {
        return jobs.size();
    }
    public Job current() {
        return jobs.get(jobs.size()-1);
    }
}