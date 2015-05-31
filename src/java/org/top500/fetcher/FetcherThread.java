package org.top500.fetcher;

import org.top500.schema.Schema;
import org.top500.utils.DateUtils;
import org.top500.utils.LocationUtils;
import org.top500.utils.Configuration;

import java.lang.Override;
import java.lang.Thread;

import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;

import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedCondition;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.titleIs;
import static org.openqa.selenium.support.ui.ExpectedConditions.titleContains;
//import static org.openqa.selenium.support.ui.ExpectedConditions.urlToBe;
//import static org.openqa.selenium.support.ui.ExpectedConditions.urlContains;
//import static org.openqa.selenium.support.ui.ExpectedConditions.urlMatches;
import static org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfAllElementsLocatedBy;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfAllElementsLocatedBy;
import static org.openqa.selenium.support.ui.ExpectedConditions.textToBePresentInElement;
import static org.openqa.selenium.support.ui.ExpectedConditions.textToBePresentInElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.textToBePresentInElementValue;
import static org.openqa.selenium.support.ui.ExpectedConditions.frameToBeAvailableAndSwitchToIt;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeSelected;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementSelectionStateToBe;
import static org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent;

import static org.top500.fetcher.WaitingConditions.onlywait;
import static org.top500.fetcher.WaitingConditions.newWindowIsOpened;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class FetcherThread extends Thread {
    public static final Logger LOG = LoggerFactory.getLogger(FetcherThread.class);
    private final Schema _schema;
    private volatile Throwable _throwable;
    private final Joblist _joblist = new Joblist();
    private static int fetch_n_pages = 2;
    private static int fetch_n_jobs_perpage = 2;
    private static int fetch_n_days = 7;
    private static String driver_download_directory;

    private WebDriver driver = null;
    private Wait<WebDriver> wait = null;
    private int driver_wait;

    private Stack windows_stack = new Stack();

    public FetcherThread(String name, Schema schema) {
        super(name);
        this._schema = schema;
        Configuration conf = Configuration.getInstance();
        fetch_n_pages = conf.getInt("fetch.first.n.pages", 2);
        fetch_n_jobs_perpage = conf.getInt("fetch.first.n.jobs.perpage", 2);
        fetch_n_days = conf.getInt("fetch.winthin.n.days.pages", 7);
        driver_wait = conf.getInt("fetch.webdriver.wait.default", 5);
        driver_download_directory = conf.get("fetch.webdriver.download.dir", "/tmp");
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
            LOG.info(this.getName() + " for " + _schema.getName() + " start .... (no notifier)");
        }

        try {

            String subdir = Long.toString(Thread.currentThread().getId());
            driver = WebDriverService.getWebDriver("http://127.0.0.1:8899", driver_download_directory + "/" + subdir);
            wait = new WebDriverWait(driver, driver_wait);

            fetch();

            //Thread.sleep(1000);
        } catch (Throwable t) {
            _throwable = t;
            LOG.warn("Exception for thread " + this.getName());
        } finally {
            if ( driver != null ) driver.quit();
        }

        if ( notifier != null ) {
            notifier.fireFinished(this);
        } else {
            LOG.info(this.getName() + " for " + _schema.getName() + " finish (no notifier)");
        }
    }

    @Override
    public void run() {
        //this is the original JDK thread
        LOG.info("Not in Threadpool mode");
        run(null);
    }

    private void fetch() throws Exception {
        try {
            String window = driver.getWindowHandle();
            LOG.debug("Inital window handle: " + window);
            windows_stack.push(window);

            Actions(null, _schema.actions);
            Procedure(_schema.procedure, null);
        } catch ( Exception e ) {
            LOG.warn("Exception: ", e);
        }
        //Thread.sleep(5000);
    }

    ////////////////////////driver part///////////////////////
    private By getLocator(String xpath_prefix, Schema.Element element) {
        if ( element == null ) {
            return null;
        }
        if ( xpath_prefix == null ) xpath_prefix="";
        By locator = null;
        String val = element.element;
        if ( element.how == null ) {
            locator = By.xpath(xpath_prefix+val); /* default using xpath */
        } else {
            switch (element.how) {
                case "CLASS_NAME": locator = By.className(val); break;
                case "CSS": locator = By.cssSelector(val); break;
                case "ID": locator = By.id(val); break;
                case "LINK_TEXT":locator = By.linkText(val); break;
                case "NAME":locator = By.name(val); break;
                case "PARTIAL_LINK_TEXT":locator = By.partialLinkText(val); break;
                case "TAG_NAME":locator = By.tagName(val); break;
                case "XPATH":locator = By.xpath(xpath_prefix+val); break;
                default: return null;
            }
        }
        return locator;
    }

    private boolean Action(String xpath_prefix, Schema.Action action) {
        LOG.debug("Action on " + (xpath_prefix==null?"":xpath_prefix) + action.element.element);

        String currentWindowHandle = driver.getWindowHandle();
        Set<String> currentWindowHandles = driver.getWindowHandles();
        for(String handle : currentWindowHandles) {
            LOG.debug("existing window handle: " + handle);
        }

        if  ( action.command.code == Schema.CmdType.Load ) {
            LOG.debug("Action load " + action.element.element);
            driver.get(action.element.element);
        } else if ( action.command.code == Schema.CmdType.Back ) {
            LOG.debug("Action navigate back");
            driver.navigate().back();
        } else if ( action.command.code == Schema.CmdType.Forward ) {
            LOG.debug("Action navigate forward");
            driver.navigate().forward();
        } else if ( action.command.code == Schema.CmdType.Refresh ) {
            LOG.debug("Action refresh");
            driver.navigate().refresh();
        } else if ( action.command.code == Schema.CmdType.Restore ) {
            String topWindowHandle = (String)windows_stack.peek();
            if ( !driver.getWindowHandle().equals(topWindowHandle) ) {
                LOG.warn("Something wrong to restore, current window not equal topwindow");
                return false;
            } else {
                windows_stack.pop();
                String previousWindowHandle = (String)windows_stack.peek();
                LOG.debug("Restore saved window" + previousWindowHandle);
                driver.close();
                driver.switchTo().window(previousWindowHandle);
            }
        } else {
            By locator = getLocator(xpath_prefix, action.element);
            WebElement element = locator.findElement(driver);
            switch (action.command.code) {
                case Click:
                    try {
                        element.click();
                    } catch ( WebDriverException e ) {
                        LOG.debug(action.element + " failed to click, reach last page?");
                        return false;
                    }
                    break;
                case Submit:
                    element.submit();
                    break;
                default: break;
            }
        }

        /* Expection handling */
        if ( action.expection == null ) {
            LOG.debug("no Expection, go on");
        } else {
            if ( action.expection.element != null )
                LOG.debug("Expection: " + action.expection.condition + " " + action.expection.element.element);
            else
                LOG.debug("Expection: " + action.expection.condition);

            By wait_locator = getLocator(null, action.expection.element);

            switch (action.expection.condition) {
                case "titleIs":
                    wait.until(titleIs(action.expection.value));
                    break;
                case "presenceOfElementLocated":
                    wait.until(presenceOfElementLocated(wait_locator));
                    break;
                case "visibilityOfElementLocated":
                    wait.until(presenceOfElementLocated(wait_locator));
                    break;
                case "elementToBeClickable":
                    wait.until(elementToBeClickable(wait_locator));
                    break;
                case "newWindowIsOpened":
                    try {
                        String newwindow = wait.until(newWindowIsOpened(currentWindowHandles));
                        windows_stack.push(newwindow);
                        driver.switchTo().window(newwindow);
                        //String handle = driver.getWindowHandle();
                        LOG.debug("new window handle: " + newwindow + " title:" + driver.getTitle());
                    } catch (Exception e) {
                        LOG.warn("Failed to open new window");
                        System.out.println("page code " + driver.getPageSource());
                    /* try frame */
                        for (int i = 0; i < 5; i++) {
                            try {
                                driver.switchTo().frame(i);
                                System.out.println("frame code " + driver.getPageSource());
                            } catch (NoSuchFrameException ee) {
                                System.out.println("frame " + i + " not exist");
                            }
                        }
                    /* try alert */
                        try {
                            wait.until(alertIsPresent());
                            LOG.debug("Yes, Alert Dialog appears");
                        } catch (Exception ee) {
                            LOG.warn("Failed to see Alert Dialog");
                        }
                    }
                    break;
                case "frameToBeAvailableAndSwitchToIt":
                    wait.until(frameToBeAvailableAndSwitchToIt(wait_locator));
                    System.out.println("frame code " + driver.getPageSource());
                    //driver.switchTo().frame("garage");
                    break;
                case "onlywait":
                    try { wait.until(onlywait()); } catch ( TimeoutException e) {}
                    LOG.debug("onlywait 5 seconds");
                    break;
                default:
                    driver.manage().timeouts().implicitlyWait(10000, MILLISECONDS);
                    LOG.debug("waited 5 seconds");
                    break;
            }
        }
        if ( action.debug ) {
            LOG.debug("source:" + driver.getPageSource());
        }
        return true;
    }

    private boolean Actions(String xpath_prefix, Schema.Actions actions) {
        if ( actions == null || actions.actions == null ) {
            LOG.debug("No Actions, return");
            return true;
        }
        for ( int i = 0; i < actions.actions.size(); i++ ) {
            boolean ret = Action(xpath_prefix, actions.actions.get(i));
            if ( !ret ) {
                return ret;
            }
        }
        return true;
    }

    private void Extracts(String xpath_prefix, Schema.Extracts extracts, Job job){
        if ( extracts == null ) {
            LOG.debug("Nothing to be extracted, return");
            return;
        }
        if ( job == null) {
            LOG.warn("Nont know where to save extracts, return");
            return;
        }
        try {
            for (String key : extracts.items.keySet()) {
                Schema.Element ele = extracts.items.get(key);
                if ( (ele.how != null) && ele.how.equals("url") ) {
                    job.addField(Job.JOB_URL, driver.getCurrentUrl());
                    LOG.debug("job_url" + ":" + driver.getCurrentUrl());
                } else {
                    By locator = getLocator(xpath_prefix, ele);
                    WebElement element = locator.findElement(driver);
                    String value;
                    if ( key.equals(Job.JOB_DESCRIPTION) )
                        value = element.getAttribute("innerHTML");
                    else
                        value = element.getText();

                    if ( key.equals(Job.JOB_DATE) ) {
                        value = DateUtils.formatDate(value, _schema.getJob_date_format());
                    }

                    if ( key.equals(Job.JOB_LOCATION) ) {
                        value = LocationUtils.format(value, _schema.getJob_location_format_regex());
                    }

                    job.addField(key, value);
                    LOG.debug(key + ":" + value);
                }
            }

        } catch ( Exception e ) {
            LOG.warn("Extracts failed", e);
        }
    }

    private void Procedure(Schema.Procedure procedure, Job job) {
        if ( procedure == null ) return;
        if ( procedure.loop_type == Schema.LOOP_TYPE.BEGIN ) {
            LOG.debug("Procedure: loop of BEGIN type for job list");
            String xpath_prefix_loop = procedure.xpath_prefix_loop;
            if (xpath_prefix_loop != null && !xpath_prefix_loop.isEmpty()) {
                By locator = By.xpath(xpath_prefix_loop);
                List<WebElement> elements = locator.findElements(driver);
                LOG.debug("Procedure: loop of BEGIN type, find " + elements.size() + " with " + xpath_prefix_loop);
                int number = (elements.size()>fetch_n_jobs_perpage) ? fetch_n_jobs_perpage : elements.size();
                for (int i = 0; i < number; i++) {
                    final Job newjob = new Job();
                    newjob.addField(Job.JOB_COMPANY, _schema.getName());
                    String newprefix = xpath_prefix_loop + "[" + Integer.toString(i+1) + "]/";
                    Extracts(newprefix, procedure.extracts, newjob);

                    if ( DateUtils.nDaysAgo(newjob.getField(Job.JOB_DATE), fetch_n_days) ) {
                        LOG.debug("Job older than configured date, ignore");
                        continue;
                    }
                    Actions(newprefix, procedure.actions);
                    Procedure(procedure.procedure, newjob);
                    _joblist.addJob(newjob);
                }
            } else {
                LOG.warn("Procedure: loop of BEGIN type, dont have xpath_prefix");
            }
        } else if ( procedure.loop_type == Schema.LOOP_TYPE.END ) {
            LOG.debug("Procedure: loop of END type for page list");
            int pages = 0;
            do {
                Procedure(procedure.procedure, null);
                pages++;
            } while (Actions(null, procedure.actions) && (pages<fetch_n_pages));
        } else {
            LOG.debug("Procedure: no loop for job");
            Extracts(null, procedure.extracts, job);
            Actions(null, procedure.actions);
        }
    }
}