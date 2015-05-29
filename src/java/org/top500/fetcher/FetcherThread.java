package org.top500.fetcher;

import org.top500.schema.*;

import java.lang.Override;
import java.lang.Thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;

import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedCondition;

public class FetcherThread extends Thread {
    public static final Logger LOG = LoggerFactory.getLogger(FetcherThread.class);
    private final Schema _schema;
    private volatile Throwable _throwable;
    private final Joblist _joblist = null;
    public FetcherThread(String name, Schema schema) {
        super(name);
        this._schema = schema;
    }
    public Throwable getThrowable() {
        return _throwable;
    }
    public Joblist getJoblist() {
        return _joblist;
    }

    public void run(RunNotifier notifier) {
        /* to be runned in threadpool mode, will notify the listener about progress */
        if ( notifier != null ) {
            notifier.fireStarted(this);
        } else {
            LOG.info(this.getName() + " for " + _schema.name + " start .... (no notifier)");
        }

        WebDriver driver = null;
        try {
            String download_directory = "/sdk/tmp/chrome/download";
            String subdir = Long.toString(Thread.currentThread().getId());
            driver = WebDriverService.getWebDriver("http://127.0.0.1:8899", download_directory + "/" + subdir);

            work(driver);

        } catch (Throwable t) {
            _throwable = t;
            LOG.warn("Exception for thread " + this.getName());
        } finally {
            if ( driver != null ) driver.quit();
        }

        if ( notifier != null ) {
            notifier.fireFinished(this);
        } else {
            LOG.info(this.getName() + " for " + _schema.name + " finish (no notifier)");
        }
    }

    @Override
    public void run() {
        //this is the original JDK thread
        LOG.info("Not in Threadpool mode");
        run(null);
    }

    private void work(WebDriver driver) throws Exception {
        Thread.sleep(10000);
    }
}