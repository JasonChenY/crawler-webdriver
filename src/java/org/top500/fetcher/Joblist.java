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
    public Job get(int index) {
        if ( index < jobs.size() )
            return jobs.get(index);
        else
            return null;
    }
    public Job current() {
        if ( jobs.size() == 0 )
            return null;
        else
            return jobs.get(jobs.size()-1);
    }
}