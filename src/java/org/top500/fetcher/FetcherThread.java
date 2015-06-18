package org.top500.fetcher;

import org.top500.schema.Schema;
import org.top500.utils.DateUtils;
import org.top500.utils.LocationUtils;
import org.top500.utils.StringUtils;
import org.top500.utils.Configuration;
import org.top500.schema.Schema.JobUniqueIdCalc;

import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.lang.Thread;

import java.util.*;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

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
import static org.top500.fetcher.WaitingConditions.elementValueChanged;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.apache.oro.text.perl.MalformedPerl5PatternException;
import org.apache.oro.text.perl.Perl5Util;

public class FetcherThread extends Thread {
    public static final Logger LOG = LoggerFactory.getLogger(FetcherThread.class);
    public final Schema _schema;
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
            _schema.fetch_result = false;
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
                if (expection.condition.equals("elementTextChanged") ||
                    expection.condition.equals("elementValueChanged") ) {
                    By expect_locator = getLocator(null, 0, expection.element);
                    try {
                        WebElement currentElement = expect_locator.findElement(driver);
                        if ( expection.condition.equals("elementTextChanged" ) )
                            currentTexts.add(currentElement.getText());
                        else if ( expection.condition.equals("elementValueChanged" ) )
                            currentTexts.add(currentElement.getAttribute("value"));
                    } catch ( NoSuchElementException e ) {
                        LOG.warn("Expected element with " + expection.element.element + " not found, return " , e);
                        return (action.isFatal ? false : true);
                    }
                }
            }
        }

        if ( action.command.code == Schema.CmdType.None ) {
            return true;
        } else if ( action.command.code == Schema.CmdType.Load ) {
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
                return (action.isFatal ? false : true);
            } else {
                windows_stack.pop();
                String previousWindowHandle = (String) windows_stack.peek();
                LOG.debug("Restore saved window: " + previousWindowHandle);
                driver.close();
                driver.switchTo().window(previousWindowHandle);
            }
        } else if ( action.command.code == Schema.CmdType.ScrollIntoView ) {
            LOG.debug("Action ScrollIntoView");
            boolean ret = scrolIntoView(xpath_prefix, index, action.element);
            if (!ret) return (action.isFatal ? false : true);
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
        } else if ( action.command.code == Schema.CmdType.switchToMainFrame ) {
            LOG.debug("switchToMainFrame");
            driver.switchTo().defaultContent();
            //driver.switchTo().frame(0);
        } else {
            String dbgstr = formatXpath(xpath_prefix, index, action.element.element);

            By locator = getLocator(xpath_prefix, index, action.element);
            WebElement element;
            try {
                element = locator.findElement(driver);
            } catch ( NoSuchElementException e ) {
                LOG.warn("Element with " + dbgstr + " not found, return, >>> Exception: " , e);
                return (action.isFatal ? false : true);
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
                        return (action.isFatal ? false : true);
                    }
                    break;
                case Submit:
                    LOG.debug("submit " + dbgstr);
                    element.submit();
                    break;
                case selectByVisibleText:
                case selectByValue:
                    LOG.debug("select " + action.setvalue + " for " + dbgstr);
                    Select dropdown = new Select(element);
                    if ( action.command.code == Schema.CmdType.selectByVisibleText )
                        dropdown.selectByVisibleText(action.setvalue);
                    else
                        dropdown.selectByValue(action.setvalue);
                    break;
                case openInNewTab:
                    LOG.debug("openInNewTab " + dbgstr);
                    new Actions(driver).keyDown(Keys.CONTROL).click(element).keyUp(Keys.CONTROL).build().perform();
                    break;
                case sendKeys:
                    LOG.debug("sendKeys " + action.setvalue + " to " + dbgstr);
                    element.sendKeys(action.setvalue);
                    break;
                case setPage:
                    LOG.debug("setPage (sendKeys) " + index + " to " + dbgstr);
                    //element.clear(); element.sendKeys(String.valueOf(index));
                    new Actions(driver).clickAndHold(element).sendKeys(Keys.chord(Keys.CONTROL, "a"), String.valueOf(index)).build().perform();
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
                            LOG.debug("newWindowIsOpened: " + newwindow + " title:" + driver.getTitle());
                        } catch (Exception e) {
                            LOG.warn("Failed to open new window");
                            return (action.isFatal ? false : true);
                            /*
                            for (int i = 0; i < 5; i++) {
                                try {
                                    driver.switchTo().frame(i);
                                    System.out.println("frame code " + driver.getPageSource());
                                } catch (NoSuchFrameException ee) {
                                    System.out.println("frame " + i + " not exist");
                                }
                            }
                            try {
                                wait.until(alertIsPresent());
                                LOG.debug("Yes, Alert Dialog appears");
                            } catch (Exception ee) {
                                LOG.warn("Failed to see Alert Dialog");
                            }
                            */
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
                    case "elementValueChanged":
                        String currentValue = currentTexts.poll();
                        LOG.debug("Current value: " + currentValue);
                        String newvalue = wait.until(elementValueChanged(wait_locator, currentValue));
                        LOG.debug("Element value changed to " + newvalue);
                        break;
                    case "elementToBeSelected":
                        wait.until(elementToBeSelected(wait_locator));
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
            return true;
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
                    Boolean formatted = false;
                    List<String> values = new ArrayList<String>();
                    List<WebElement> elements = locator.findElements(driver);
                    LOG.debug("Located " + elements.size() + " elements for extracing " + key);
                    for ( int i = 0; i < elements.size(); i++ ) {
                        if ( ele.method != null ) {
                            switch ( ele.method ) {
                                case "getValue":
                                    value = elements.get(i).getAttribute("value");
                                    break;
                                case "innerHTML":
                                    value = elements.get(i).getAttribute("innerHTML");
                                    break;
                                case "getText":
                                default:
                                    value = elements.get(i).getText();
                            }
                        } else {
                            if (key.equals(Job.JOB_DESCRIPTION))
                                value = elements.get(i).getAttribute("innerHTML");
                            else
                                value = elements.get(i).getText();
                        }

                        if ( ele.transforms != null ) {
                            // Firstly handle transform against single item.
                            for ( int j = 0; j < ele.transforms.size(); j++ ) {
                                Schema.Transform transform = ele.transforms.get(j);
                                if ( transform == null || transform.value == null || transform.value.isEmpty() ) continue;
                                switch ( transform.how ) {
                                    case "insertBefore":
                                        value = transform.value + value;
                                        break;
                                    case "appendAfter":
                                        value = value + transform.value;
                                        break;
                                    case "regex":
                                        try {
                                            Perl5Util plutil = new Perl5Util();
                                            value = plutil.substitute(transform.value, value);
                                        } catch (MalformedPerl5PatternException me) {
                                            LOG.warn("regex faield" , me);
                                        }
                                        break;
                                    case "dateFormat":
                                        value = DateUtils.formatDate(value, transform.value);
                                        formatted = true;
                                        break;
                                    case "location_regex":
                                        value = LocationUtils.format(value, transform.value);
                                        formatted = true;
                                        break;
                                    default: break;
                                }
                            }
                        }
                        values.add(value);
                    }
                    // handle Transform again all the elements, for exampe to concatenate them (default), or regex to form a new string
                    boolean joined = false;
                    if ( ele.transforms != null ) {
                        for (int j = 0; j < ele.transforms.size(); j++) {
                            Schema.Transform transform = ele.transforms.get(j);
                            if (transform == null) continue;
                            switch (transform.how) {
                                case "regex_on_all":
                                    // TODO
                                    joined = true;
                                    break;
                                case "join":
                                    if (transform.value == null || transform.value.isEmpty())
                                        value = org.apache.commons.lang3.StringUtils.join(values.toArray(), ",");
                                    else
                                        value = org.apache.commons.lang3.StringUtils.join(values.toArray(), transform.value);
                                    joined = true;
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    if ( !joined ) {
                        // default cases for job description.
                        value = org.apache.commons.lang3.StringUtils.join(values.toArray(),"<BR/>");
                    }

                    if (key.equals(Job.JOB_DESCRIPTION) || key.equals(Job.JOB_TITLE)) {
                        value = StringUtils.stripNonCharCodepoints(value);
                    }

                    LOG.debug(key + ":" + (value.length()>100?value.substring(0,100):value));

                    if ( !formatted ) {
                        // some default handling to avoid config item in schema
                        if (key.equals(Job.JOB_DATE) || key.equals(Job.JOB_EXPIRE)) {
                            value = DateUtils.formatDate(value);
                        }

                        if (key.equals(Job.JOB_LOCATION)) {
                            if (_schema.job_regex_matcher_for_location != null) {
                                value = LocationUtils.match(value,
                                        _schema.job_regex_matcher_for_location.regex,
                                        _schema.job_regex_matcher_for_location.which,
                                        _schema.job_regex_matcher_for_location.group);
                            }
                            value = LocationUtils.format(value);
                        }
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
    private void PostProcessJob(final Job newjob, final Schema _schema) {
        // Generate unique job id
        JobUniqueIdCalc calc = _schema.job_unique_id_calc;
        if (calc != null && calc.how != null) {
            if (calc.how.equals("url_plus_title")) {
                newjob.addField(Job.JOB_UNIQUE_ID, newjob.getField(Job.JOB_URL) + newjob.getField(Job.JOB_TITLE));
            } else if (calc.how.equals("regex_on_url")) {
                if ( calc.value != null && !calc.value.isEmpty() ) {
                    try {
                        Perl5Util plutil = new Perl5Util();
                        String newurl = plutil.substitute(calc.value, newjob.getField(Job.JOB_URL));
                        newjob.addField(Job.JOB_UNIQUE_ID, newurl);
                    } catch (MalformedPerl5PatternException me) {
                        LOG.warn("Failed to generate unique id for job via regex " + calc.value);
                        newjob.addField(Job.JOB_UNIQUE_ID, newjob.getField(Job.JOB_URL));
                    }
                } else {
                    LOG.warn("No regex to generate unique id for job");
                    newjob.addField(Job.JOB_UNIQUE_ID, newjob.getField(Job.JOB_URL));
                }
            }
        } else {
            newjob.addField(Job.JOB_UNIQUE_ID, newjob.getField(Job.JOB_URL));
        }

        if (!newjob.getFields().containsKey(Job.JOB_DATE) ) {
            LOG.info("No Job_date field extracted, use current time");
            newjob.addField(Job.JOB_DATE, DateUtils.getCurrentDate());
        }

        if ( newjob.getFields().containsKey(Job.JOB_COMPANY_SUBNAME) ) {
            String subname = newjob.getField(Job.JOB_COMPANY_SUBNAME);
	    if ( subname != null && !subname.isEmpty() ) {
		newjob.addField(Job.JOB_COMPANY, subname);
	    }
	    // This might be changed later,
	    // Now Fetcher will combine company name and subname together via Transforms,
	    // Finally we might need both fields to make client easier.
	    // which means to disable the combination in schema and comment this section of code
	    newjob.removeField(Job.JOB_COMPANY_SUBNAME);
	}
    }
    private void Procedure(Schema.Procedure procedure, Job job) {
        if ( procedure == null ) return;
        if ( procedure.loop_type == Schema.LOOP_TYPE.BEGIN ) {
            if ( procedure.loop_for_pages ) {
                LOG.debug("Procedure: loop of BEGIN type for page list");
                int loop_totalpages = 0;
                String xpath_prefix_loop = procedure.xpath_prefix_loop;
                if (xpath_prefix_loop != null && !xpath_prefix_loop.isEmpty()) {
                    By locator = By.xpath(xpath_prefix_loop);
                    List<WebElement> elements = locator.findElements(driver);
                    loop_totalpages = elements.size();
                    LOG.debug("Procedure: loop of BEGIN type, find " + loop_totalpages + " pages with " + xpath_prefix_loop);
                } else if ( procedure.loop_totalpages != null ) {
                    By locator = getLocator(null, 0, procedure.loop_totalpages);
                    try {
                        WebElement element = locator.findElement(driver);
                        loop_totalpages = Integer.parseInt(element.getText());
                        LOG.debug("Procedure: loop of BEGIN type, find " + loop_totalpages + " with loop_totalpages");
                    } catch ( NoSuchElementException e ) {
                        LOG.warn("Procedure: loop of BEGIN type, loop_totalpages defined, but failed to parse valid number from this element");
                        return;
                    }
                } else {
                    LOG.warn("Procedure: loop of BEGIN type, but neither xpath_prefix nor loop_totalpages, cant continue");
                    return;
                }

                if ( _schema.fetch_cur_pages == -1 ) _schema.fetch_cur_pages = procedure.begin_from;
                for (; _schema.fetch_cur_pages < loop_totalpages + procedure.end_to; _schema.fetch_cur_pages++) {
                    // some site wont have the next page button, should use the for loop as well for page navigation
                    // but we should not click the page Anchor in the first round, go to Procedure directly.
                    if (_schema.fetch_cur_pages != procedure.begin_from ) {
                        boolean result = true;
                        if ( procedure.loop_totalpages != null ) {
                            // via normal loop with index, in general get the toal pages firstly, then set "input" with index
                            result = Actions(null, _schema.fetch_cur_pages+1, procedure.actions);
                        } else {
                            // via the xpath prefix
                            String newprefix = procedure.xpath_prefix_loop + "[" + Integer.toString(_schema.fetch_cur_pages + 1) + "]/";
                            result = Actions(newprefix, 0, procedure.actions);
                        }
                        if ( ! result ) {
                            LOG.info("Actions for going to next page failed, break");
                            break;
                        }
                    }
                    Procedure(procedure.procedure, null);
                    if ( (_schema.fetch_cur_pages-procedure.begin_from+1) >= fetch_n_pages) {
                        LOG.debug("Fetched " + fetch_n_pages + " pages, reach configured limit, return");
                        break;
                    }
                }
            } else {
                LOG.debug("Procedure: loop of BEGIN type for job list");
                String xpath_prefix_loop = procedure.xpath_prefix_loop;
                if (xpath_prefix_loop != null && !xpath_prefix_loop.isEmpty()) {
                    By locator = By.xpath(xpath_prefix_loop);
                    List<WebElement> elements = locator.findElements(driver);
                    LOG.debug("Procedure: loop of BEGIN type, find " + elements.size() + " jobs with " + xpath_prefix_loop);
                    if ( _schema.fetch_cur_jobs == -1 ) _schema.fetch_cur_jobs = procedure.begin_from;
                    for (; _schema.fetch_cur_jobs < elements.size() + procedure.end_to; _schema.fetch_cur_jobs++) {
                        final Job newjob = new Job();
                        newjob.addField(Job.JOB_COMPANY, _schema.getName());
                        String newprefix = xpath_prefix_loop + "[" + Integer.toString(_schema.fetch_cur_jobs + 1) + "]/";
                        if (!Extracts(newprefix, _schema.fetch_cur_jobs + 1, procedure.extracts, newjob)) {
                            LOG.debug("Failed to extract info for this job, ignore");
                            continue;
                        }

                        if (DateUtils.nDaysAgo(newjob.getField(Job.JOB_DATE), fetch_n_days)) {
                            LOG.debug("Job older than configured date, ignore");
                            continue;
                        }
                        Actions(newprefix, 0, procedure.actions);
                        Procedure(procedure.procedure, newjob);

                        PostProcessJob(newjob, _schema);

                        _joblist.addJob(newjob);

                        if ((_schema.fetch_cur_jobs-procedure.begin_from+1) >= fetch_n_jobs_perpage) {
                            LOG.debug("Fetched " + fetch_n_jobs_perpage + " jobs, reach configured limit, return");
                            break;
                        }
                    }
                    _schema.fetch_cur_jobs = -1;
                } else {
                    LOG.warn("Procedure: loop of BEGIN type, dont have xpath_prefix");
                }
            }
        } else if ( procedure.loop_type == Schema.LOOP_TYPE.END ) {
            LOG.debug("Procedure: loop of END type for page list");
            boolean result = true;
            if ( _schema.fetch_cur_pages != -1 ) {
                int tmppages = 0;
                do {
                    // move forward to the pages failed last time.
                } while ( (tmppages++ < _schema.fetch_cur_pages) && (result=Actions(null, 0, procedure.actions)) );
                if ( !result ) {
                    LOG.warn("Failed again during moving to the failed place last time");
                    return;
                }
            } else {
                _schema.fetch_cur_pages = 0;
            }

            do {
                Procedure(procedure.procedure, null);
            } while ( (++_schema.fetch_cur_pages<fetch_n_pages) && (result=Actions(null, 0, procedure.actions)) );
            if ( !result ) {
                LOG.info("Actions for going to next page failed, break");
            } else {
                LOG.debug("Fetched " + fetch_n_pages + " pages, reach configured limit, return");
            }
        } else {
            LOG.debug("Procedure for single job");
            Extracts(null, 0, procedure.extracts, job);
            Actions(null, 0, procedure.actions);
        }
    }
}
