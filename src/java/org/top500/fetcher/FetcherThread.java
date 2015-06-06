package org.top500.fetcher;

import org.top500.schema.Schema;
import org.top500.utils.DateUtils;
import org.top500.utils.LocationUtils;
import org.top500.utils.Configuration;

import java.lang.Integer;
import java.lang.Override;
import java.lang.Thread;

import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.Stack;
import java.util.LinkedList;
import java.util.Queue;
import java.io.FileWriter;
import java.io.BufferedWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.interactions.Actions;
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
import org.openqa.selenium.support.ui.Select;

import static org.top500.fetcher.WaitingConditions.onlywait;
import static org.top500.fetcher.WaitingConditions.newWindowIsOpened;
import static org.top500.fetcher.WaitingConditions.elementTextChanged;

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
        fetch_n_pages = conf.getInt("fetch.first.n.pages", 1000);
        fetch_n_jobs_perpage = conf.getInt("fetch.first.n.jobs.perpage", 1000);
        fetch_n_days = conf.getInt("fetch.winthin.n.days.pages", 180);
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

            Actions(null, 0, _schema.actions);
            Procedure(_schema.procedure, null);
        } catch ( Exception e ) {
            LOG.warn("Exception: ", e);
        }
        //Thread.sleep(5000);
    }

    ////////////////////////driver part///////////////////////
    public static void save_page_content(String content) {
        try {
            String date = DateUtils.getThreadLocalDateFormat().format(new Date());
            String suffix = ".html";
            String fname = "/tmp/" + date + suffix;
            FileWriter fw = new FileWriter(fname, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.flush();
            bw.close();
            fw.close();
            LOG.debug("Content saved to " + fname);
        } catch (Exception ee) {
            LOG.warn("Failed to save content to file " + ee.getMessage());
        };
    }
    private String formatXpath(String prefix, int index, String tgt) {
        String xpath;
        if ( tgt.startsWith("//") && index != 0 ) {
            // Array of sibling elements, but need access children of them
            // Absolute xpath expression with a %d in the schema
            xpath = String.format(tgt, index);
        } else if ( prefix != null ) {
            // Normal case, array of elements, and share same parent
            xpath = prefix + tgt;
        } else {
            // single absolute xpath
            xpath = tgt;
        }
        return xpath;
    }
    private By getLocator(String xpath_prefix, int index, Schema.Element element) {
        if ( element == null ) {
            return null;
        }
        By locator = null;
        String val = element.element;
        if ( element.how == null || element.how.equals("XPATH") ) {
            String xpath = formatXpath(xpath_prefix, index, val);
            locator = By.xpath(xpath);
            LOG.debug("Locating element via " + xpath);
        } else {
            switch (element.how) {
                case "CLASS_NAME": locator = By.className(val); break;
                case "CSS": locator = By.cssSelector(val); break;
                case "ID": locator = By.id(val); break;
                case "LINK_TEXT":locator = By.linkText(val); break;
                case "NAME":locator = By.name(val); break;
                case "PARTIAL_LINK_TEXT":locator = By.partialLinkText(val); break;
                case "TAG_NAME":locator = By.tagName(val); break;
                default: return null;
            }
            LOG.debug("Locating element via " + val);
        }
        return locator;
    }

    private boolean scrolIntoView(String xpath_prefix, int index, Schema.Element element) {
        if ( element == null ) return true;
        String statement = "";
        if ( element.how == null || element.how.equals("XPATH") ) {
            String xpath = formatXpath(xpath_prefix, index, element.element);
            statement = String.format("var element = document.evaluate(\"%s\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; element.scrollIntoView();", xpath);
        } else if ( element.how.equals("CLASS_NAME") ) {
            statement = String.format("var element = document.getElementsByClassName(\"%s\")[0]; element.scrollIntoView();", element.element);
        } else if ( element.how.equals("ID") ) {
            statement = String.format("document.getElementById(\"%s\").scrollIntoView();", element.element);
        } else {
            LOG.warn("Dont support this manner to locate element in JS " + element.how);
            return false;
        }
        LOG.debug("execute JS: " + statement);
        ((JavascriptExecutor)driver).executeScript(statement);
        return true;
    }
    private boolean Action(String xpath_prefix, int index, Schema.Action action) {
        // save some necessary inforamtion before action
        String currentWindowHandle = driver.getWindowHandle();
        Set<String> currentWindowHandles = driver.getWindowHandles();
        for(String handle : currentWindowHandles) {
            LOG.debug("existing window handle: " + handle);
        }

        // current text value or value for expected elements
        Queue<String> currentTexts =new LinkedList<String>();
        if ( action.expections != null ) {
            for (int iter = 0; iter < action.expections.expections.size(); iter++) {
                Schema.Expection expection = action.expections.expections.get(iter);
                if (expection == null || expection.condition == null) continue;
                if ( expection.condition.equals("elementTextChanged") /*|| other */) {
                    By expect_locator = getLocator(null, 0, expection.element);
                    try {
                        WebElement currentElement = expect_locator.findElement(driver);
                        currentTexts.add(currentElement.getText());
                    } catch ( NoSuchElementException e ) {
                        LOG.warn("Expected element with " + expection.element.element + " not found, return " , e);
                        return false;
                    }
                }
            }
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
            String topWindowHandle = (String) windows_stack.peek();
            if (!driver.getWindowHandle().equals(topWindowHandle)) {
                LOG.warn("Action restore, but something wrong, current window not equal topwindow");
                return false;
            } else {
                windows_stack.pop();
                String previousWindowHandle = (String) windows_stack.peek();
                LOG.debug("Restore saved window" + previousWindowHandle);
                driver.close();
                driver.switchTo().window(previousWindowHandle);
            }
        } else if ( action.command.code == Schema.CmdType.ScrollIntoView ) {
            LOG.debug("Action ScrollIntoView");
            boolean ret = scrolIntoView(xpath_prefix, index, action.element);
            if (!ret) return false;
        } else if ( action.command.code == Schema.CmdType.zoom ) {
            /* not working yet */
            LOG.debug("Action zoom window");
            try {
                int val = Integer.parseInt(action.setvalue);
                int number = Math.abs(val);
                Keys key = (val > 0) ? Keys.ADD : Keys.SUBTRACT;
                WebElement html = driver.findElement(By.tagName("html"));

                for ( int i = 0; i < number; i ++ ) {
                    html.sendKeys(Keys.chord(Keys.CONTROL, key));
                }
            } catch ( Exception e ) {
                LOG.warn("failed to zoom window", e);
            }
        } else {
            String dbgstr = formatXpath(xpath_prefix, index, action.element.element);

            By locator = getLocator(xpath_prefix, index, action.element);
            WebElement element;
            try {
                element = locator.findElement(driver);
            } catch ( NoSuchElementException e ) {
                LOG.warn("Element with " + dbgstr + " not found, return " , e);
                return false;
            }

            switch (action.command.code) {
                case Click:
                    LOG.debug("Click " + dbgstr);
                    try {
                        //String statement = String.format("window.scrollTo(%d, %d);", element.getLocation().getX(), element.getLocation().getY());
                        //((JavascriptExecutor)driver).executeScript(statement);

                        //((org.openqa.selenium.internal.Locatable) element).getCoordinates().inViewPort();

                        //new org.openqa.selenium.interactions.Actions(driver).moveToElement(element).perform();
                        //element.click();

                        //org.openqa.selenium.interactions.Actions actions = new org.openqa.selenium.interactions.Actions(driver);
                        //actions.moveToElement(element).click().perform();

                        //((JavascriptExecutor) driver).executeScript("window.focus();");

                        //WebElement abc = driver.switchTo().activeElement();
                        //LOG.debug(abc.getAttribute("id") + "   " + abc.getAttribute("name") + "   " + abc.getAttribute("class"));

                        //((org.openqa.selenium.internal.Locatable) element).getCoordinates().onScreen();
                        //long yOffset = (Long)((JavascriptExecutor)driver).executeScript("return arguments[0].scrollTop;", element);

                        //scrolIntoView(xpath_prefix, action.element);
                        element.click();
                    } catch ( WebDriverException e ) {
                        LOG.debug("Failed to click " + dbgstr + " reach last page?");
                        return false;
                    }
                    break;
                case Submit:
                    LOG.debug("submit " + dbgstr);
                    element.submit();
                    break;
                case selectByVisibleText:
                case selectByValue:
                    LOG.debug("select " + dbgstr);
                    Select dropdown = new Select(element);
                    if ( action.command.code == Schema.CmdType.selectByVisibleText )
                        dropdown.selectByVisibleText(action.setvalue);
                    else
                        dropdown.selectByValue(action.setvalue);
                    break;
                case openInNewTab:
                    LOG.debug("Action openInNewTab");
                    new Actions(driver).keyDown(Keys.CONTROL).click(element).keyUp(Keys.CONTROL).build().perform();
                    break;
                default: break;
            }
        }

        /* Expection handling */
        if ( action.expections == null ) {
            LOG.debug("no Expection, go on");
        } else {
            for ( int iter = 0; iter < action.expections.expections.size(); iter++ ) {
                Schema.Expection expection = action.expections.expections.get(iter);
                if ( expection == null || expection.condition == null ) continue;
                if (expection.element != null)
                    LOG.debug("Expection: " + expection.condition + " " + expection.element.element);
                else
                    LOG.debug("Expection: " + expection.condition);

                By wait_locator = getLocator(null, 0, expection.element);

                switch (expection.condition) {
                    case "titleIs":
                        wait.until(titleIs(expection.value));
                        break;
                    case "presenceOfElementLocated":
                        wait.until(presenceOfElementLocated(wait_locator));
                        break;
                    case "presenceOfAllElementsLocatedBy":
                        wait.until(presenceOfAllElementsLocatedBy(wait_locator));
                        break;
                    case "visibilityOfElementLocated":
                        wait.until(presenceOfElementLocated(wait_locator));
                        break;
                    case "visibilityOfAllElementsLocatedBy":
                        wait.until(visibilityOfAllElementsLocatedBy(wait_locator));
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
                        //driver.switchTo().frame("garage");
                        break;
                    case "onlywait":
                        try {
                            wait.until(onlywait());
                        } catch (TimeoutException e) {
                            LOG.debug("onlywait 5 seconds");
                        }
                        break;
                    case "elementTextChanged":
                        String currentText = currentTexts.poll();
                        LOG.debug("Current text: " + currentText);
                        String newtext = wait.until(elementTextChanged(wait_locator, currentText));
                        LOG.debug("Element text changed to " + newtext);
                        break;
                    default:
                        driver.manage().timeouts().implicitlyWait(10000, MILLISECONDS);
                        LOG.debug("waited 5 seconds");
                        break;
                }
            }
        }
        if ( action.debug ) {
            save_page_content(driver.getPageSource());
        }
        return true;
    }

    private boolean Actions(String xpath_prefix, int index, Schema.Actions actions) {
        if ( actions == null || actions.actions == null ) {
            LOG.debug("No Actions, return");
            return true;
        }
        for ( int i = 0; i < actions.actions.size(); i++ ) {
            boolean ret = Action(xpath_prefix, index, actions.actions.get(i));
            if ( !ret ) {
                return ret;
            }
        }
        return true;
    }

    private boolean Extracts(String xpath_prefix, int index, Schema.Extracts extracts, Job job){
        if ( extracts == null ) {
            LOG.debug("Nothing to be extracted, return");
            return false;
        }
        if ( job == null) {
            LOG.warn("Nont know where to save extracts, return");
            return false;
        }
        try {
            for (String key : extracts.items.keySet()) {
                Schema.Element ele = extracts.items.get(key);
                if ( (ele.how != null) && ele.how.equals("url") ) {
                    job.addField(Job.JOB_URL, driver.getCurrentUrl());
                    LOG.debug("job_url" + ":" + driver.getCurrentUrl());
                } else {
                    By locator = getLocator(xpath_prefix, index, ele);

                    String value = "";
                    if ( ele.isMultiple ) {
                        List<WebElement> elements = locator.findElements(driver);
                        for ( int i = 0; i < elements.size(); i++ ) {
                            if (key.equals(Job.JOB_DESCRIPTION))
                                value += elements.get(i).getAttribute("innerHTML") + "<BR/>";
                            else
                                value += elements.get(i).getText();
                        }
                    } else {
                        WebElement element =locator.findElement(driver);
                        if ( key.equals(Job.JOB_DESCRIPTION) )
                            value = element.getAttribute("innerHTML");
                        else
                            value = element.getText();
                    }

                    LOG.debug(key + ":" + (value.length()>100?value.substring(0,100):value));

                    if ( key.equals(Job.JOB_DATE) ) {
                        value = DateUtils.formatDate(value, _schema.getJob_date_format());
                    }

                    if ( key.equals(Job.JOB_LOCATION) ) {
                        if ( _schema.job_regex_matcher_for_location != null ) {
                            value = LocationUtils.match(value,
                                    _schema.job_regex_matcher_for_location.regex,
                                    _schema.job_regex_matcher_for_location.which,
                                    _schema.job_regex_matcher_for_location.group);
                        }
                        value = LocationUtils.format(value, _schema.getJob_location_format_regex());
                    }

                    job.addField(key, value);
                }
            }

        } catch ( Exception e ) {
            LOG.warn("Extracts failed", e);
            return false;
        }
        return true;
    }

    private void Procedure(Schema.Procedure procedure, Job job) {
        if ( procedure == null ) return;
        if ( procedure.loop_type == Schema.LOOP_TYPE.BEGIN ) {
            LOG.debug("Procedure: loop of BEGIN type for page|job list");
            String xpath_prefix_loop = procedure.xpath_prefix_loop;
            if (xpath_prefix_loop != null && !xpath_prefix_loop.isEmpty()) {
                By locator = By.xpath(xpath_prefix_loop);
                List<WebElement> elements = locator.findElements(driver);
                LOG.debug("Procedure: loop of BEGIN type, find " + elements.size() + " with " + xpath_prefix_loop);
                int number = 0;
                for (int i = procedure.begin_from; i < elements.size() + procedure.end_to; i++) {
                    if ( procedure.loop_for_pages ) {
                        // some site wont have the next page button, should use the for loop as well for page navigation
                        // but we should not click the page Anchor in the first round, go to Procedure directly.
                        if (i != procedure.begin_from ) {
                            String newprefix = xpath_prefix_loop + "[" + Integer.toString(i + 1) + "]/";
                            Actions(newprefix, 0, procedure.actions);
                        }
                        Procedure(procedure.procedure, null);
                        if ( ++number >= fetch_n_pages) {
                            LOG.debug("Fetched " + fetch_n_pages + " pages, return");
                            break;
                        }
                    } else {
                        final Job newjob = new Job();
                        newjob.addField(Job.JOB_COMPANY, _schema.getName());
                        String newprefix = xpath_prefix_loop + "[" + Integer.toString(i + 1) + "]/";
                        if (!Extracts(newprefix, i + 1, procedure.extracts, newjob)) {
                            LOG.debug("Failed to extract info for this job, ignore");
                            continue;
                        }

                        if (DateUtils.nDaysAgo(newjob.getField(Job.JOB_DATE), fetch_n_days)) {
                            LOG.debug("Job older than configured date, ignore");
                            continue;
                        }
                        Actions(newprefix, 0, procedure.actions);
                        Procedure(procedure.procedure, newjob);
                        _joblist.addJob(newjob);

                        if (++number >= fetch_n_jobs_perpage) {
                            LOG.debug("Fetched " + fetch_n_jobs_perpage + " jobs for this page, return");
                            break;
                        }
                    }
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
            } while (Actions(null, 0, procedure.actions) && (pages<fetch_n_pages));
        } else {
            LOG.debug("Procedure for single job");
            Extracts(null, 0, procedure.extracts, job);
            Actions(null, 0, procedure.actions);
        }
    }
}