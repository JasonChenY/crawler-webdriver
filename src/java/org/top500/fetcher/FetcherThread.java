package org.top500.fetcher;

import org.top500.schema.Schema;
import org.top500.utils.DateUtils;
import org.top500.utils.LocationUtils;
import org.top500.utils.StringUtils;
import org.top500.utils.CompanyUtils;
import org.top500.utils.Configuration;
import org.top500.schema.Schema.JobUniqueIdCalc;

import java.lang.*;

import java.lang.Boolean;
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

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;


public class FetcherThread extends Thread {
    public static final Logger LOG = LoggerFactory.getLogger(FetcherThread.class);
    public final Schema _schema;
    private volatile Throwable _throwable;
    private final Joblist _joblist = new Joblist();
    private int fetch_n_pages;
    private int fetch_n_jobs_perpage;
    private int fetch_n_days;
    private int fetch_n_jobs;
    private String driver_download_directory;

    private WebDriver driver = null;
    private Wait<WebDriver> wait = null;
    private int driver_wait;
    private Boolean use_proxy = false;
    private String proxy_server = null;

    private Stack windows_stack = new Stack();

    public FetcherThread(String name, Schema schema) {
        super(name);
        this._schema = schema;
        Configuration conf = Configuration.getInstance();
        fetch_n_pages = conf.getInt("fetch.n.pages", 1000);
        fetch_n_jobs_perpage = conf.getInt("fetch.n.jobs.perpage", 5000);
        fetch_n_days = conf.getInt("fetch.n.days", 180);
        fetch_n_jobs = conf.getInt("fetch.n.jobs",10000);

        driver_wait = conf.getInt("fetch.webdriver.wait.default", 5);

        driver_download_directory = conf.get("fetch.webdriver.chrome.download.dir", "/tmp");

        proxy_server = conf.get("fetch.proxy_server");
        use_proxy = conf.getBoolean("fetch.use_proxy", false);

        // check whether any local configuration existing to overwrite global settings
        if ( schema.use_proxy_specified ) {
            use_proxy = schema.use_proxy;
        }
        if ( (proxy_server == null || proxy_server.isEmpty()) && use_proxy ) {
            LOG.warn(this.getName() + ":" +"no valid proxy specified");
            use_proxy = false;
        }

        if ( schema.fetch_n_days != -1 ) {
            // local fetch_n_days specified, jobs are sorted by post date, ignore other criterias.
            fetch_n_days = schema.fetch_n_days;
            fetch_n_pages = 1000;
            fetch_n_jobs = 10000;
        } else {
            if (schema.fetch_n_pages != -1) fetch_n_pages = schema.fetch_n_pages;
            if (schema.fetch_n_jobs != -1) fetch_n_jobs = schema.fetch_n_jobs;
        }
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
            LOG.info(this.getName() + ":" +this.getName() + " for " + _schema.getName() + " start .... (no notifier)");
        }

        try {
            String subdir = Long.toString(Thread.currentThread().getId());
            driver = WebDriverService.getWebDriver(WebDriverService.DRIVER_TYPE.PHANTOMJS, driver_download_directory + "/" + subdir, use_proxy?proxy_server:null);
            wait = new WebDriverWait(driver, driver_wait);

            fetch();

            //Thread.sleep(1000);
        } catch (Throwable t) {
            LOG.warn(this.getName() + ":" +"Exception for thread " + this.getName() + ":" + t);
        } finally {
            if ( driver != null ) driver.quit();
        }

        if ( notifier != null ) {
            notifier.fireFinished(this);
        } else {
            LOG.info(this.getName() + ":" +this.getName() + " for " + _schema.getName() + " finish (no notifier)");
        }
    }

    @Override
    public void run() {
        //this is the original JDK thread
        LOG.info(this.getName() + ":" +"Not in Threadpool mode");
        run(null);
    }

    private void fetch() throws Exception {
        try {
            String window = driver.getWindowHandle();
            LOG.debug(this.getName() + ":" +"Inital window handle: " + window);
            windows_stack.push(window);

            Actions(null, 0, _schema.actions);
            Procedure(_schema.procedure, null);
        } catch ( Exception e ) {
            LOG.warn(this.getName() + ":" +"Exception: ", e);
            _schema.fetch_result = false;
        }
        //Thread.sleep(5000);
    }

    ////////////////////////driver part///////////////////////
    public void save_page_content(String content) {
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
            LOG.debug(this.getName() + ":" +"Content saved to " + fname);
        } catch (Exception ee) {
            LOG.warn(this.getName() + ":" +"Failed to save content to file " + ee.getMessage());
        };
    }
    private String formatXpath(String prefix, int index, String tgt) {
        String xpath="";
        if ( tgt.startsWith("//") && index != 0 ) {
            // Airbus case: Array of sibling elements, but need access children of them
            // Absolute xpath expression with a %d in the schema
            // This might be enchaned to use %d for relative xpath as well.
            xpath = String.format(tgt, index);
        } else if ( prefix != null ) {
            // Normal case, array of elements, and share same parent
            StringTokenizer tokenizer = new StringTokenizer(tgt, "|");
            while ( tokenizer.hasMoreTokens() ) {
                String token = tokenizer.nextToken();
                xpath += prefix + token;
                if ( tokenizer.hasMoreTokens()) xpath += "|";
            }
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
            LOG.debug(this.getName() + ":" +"Locating element via " + xpath);
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
            LOG.debug(this.getName() + ":" +"Locating element via " + val);
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
            LOG.warn(this.getName() + ":" +"Dont support this manner to locate element in JS " + element.how);
            return false;
        }
        LOG.debug(this.getName() + ":" +"execute JS: " + statement);
        ((JavascriptExecutor)driver).executeScript(statement);
        return true;
    }
    private boolean Action(String xpath_prefix, int index, Schema.Action action) {
        // any pre-condition action configured
        if ( action.preaction != null ) {
            if ( !Action(null, 0, action.preaction) ) {
                LOG.warn(this.getName() + ":" +"preaction failed");
                return false;
            }
        }
        // save some necessary inforamtion before action
        String currentWindowHandle = driver.getWindowHandle();
        Set<String> currentWindowHandles = driver.getWindowHandles();
        for(String handle : currentWindowHandles) {
            LOG.debug(this.getName() + ":" +"existing window handle: " + handle);
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
                        LOG.warn(this.getName() + ":" +"Expected element with " + expection.element.element + " not found, return " , e);
                        return (action.isFatal ? false : true);
                    }
                }
            }
        }

        if ( action.command.code == Schema.CmdType.None ) {
            LOG.info(this.getName() + ":" +"Action without command, skip");
            return true;
        } else if ( action.command.code == Schema.CmdType.Load ) {
            LOG.debug(this.getName() + ":" +"Action load " + action.element.element);
            driver.get(action.element.element);
        } else if ( action.command.code == Schema.CmdType.Back ) {
            LOG.debug(this.getName() + ":" +"Action navigate back");
            driver.navigate().back();
        } else if ( action.command.code == Schema.CmdType.Forward ) {
            LOG.debug(this.getName() + ":" +"Action navigate forward");
            driver.navigate().forward();
        } else if ( action.command.code == Schema.CmdType.Refresh ) {
            LOG.debug(this.getName() + ":" +"Action refresh");
            driver.navigate().refresh();
        } else if ( action.command.code == Schema.CmdType.Restore ) {
            String topWindowHandle = (String) windows_stack.peek();
            if (!driver.getWindowHandle().equals(topWindowHandle)) {
                LOG.warn(this.getName() + ":" +"Action restore, but something wrong, current window not equal topwindow");
                return (action.isFatal ? false : true);
            } else {
                windows_stack.pop();
                String previousWindowHandle = (String) windows_stack.peek();
                LOG.debug(this.getName() + ":" +"Restore saved window: " + previousWindowHandle);
                driver.close();
                driver.switchTo().window(previousWindowHandle);
            }
        } else if ( action.command.code == Schema.CmdType.ScrollIntoView ) {
            LOG.debug(this.getName() + ":" +"Action ScrollIntoView");
            boolean ret = scrolIntoView(xpath_prefix, index, action.element);
            if (!ret) return (action.isFatal ? false : true);
        } else if ( action.command.code == Schema.CmdType.zoom ) {
            /* not working yet */
            LOG.debug(this.getName() + ":" +"Action zoom window");
            try {
                int val = Integer.parseInt(action.setvalue);
                int number = Math.abs(val);
                Keys key = (val > 0) ? Keys.ADD : Keys.SUBTRACT;
                WebElement html = driver.findElement(By.tagName("html"));

                for ( int i = 0; i < number; i ++ ) {
                    html.sendKeys(Keys.chord(Keys.CONTROL, key));
                }
            } catch ( Exception e ) {
                LOG.warn(this.getName() + ":" +"failed to zoom window", e);
            }
        } else if ( action.command.code == Schema.CmdType.switchToMainFrame ) {
            LOG.debug(this.getName() + ":" +"switchToMainFrame");
            driver.switchTo().defaultContent();
            //driver.switchTo().frame(0);
        } else {
            String dbgstr = formatXpath(xpath_prefix, index, action.element.element);

            By locator = getLocator(xpath_prefix, index, action.element);
            WebElement element;
            try {
                element = locator.findElement(driver);
            } catch ( NoSuchElementException e ) {
                LOG.warn(this.getName() + ":" +"Element with " + dbgstr + " not found, return, >>> Exception: " , e);
                return (action.isFatal ? false : true);
            }

            switch (action.command.code) {
                case Click:
                    LOG.debug(this.getName() + ":" +"Click " + dbgstr);
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
                        //LOG.debug(this.getName() + ":" +abc.getAttribute("id") + "   " + abc.getAttribute("name") + "   " + abc.getAttribute("class"));

                        //((org.openqa.selenium.internal.Locatable) element).getCoordinates().onScreen();
                        //long yOffset = (Long)((JavascriptExecutor)driver).executeScript("return arguments[0].scrollTop;", element);

                        //scrolIntoView(xpath_prefix, action.element);
                        element.click();
                    } catch ( WebDriverException e ) {
                        LOG.debug(this.getName() + ":" +"Failed to click " + dbgstr + " reach last page?");
                        return (action.isFatal ? false : true);
                    }
                    break;
                case Submit:
                    LOG.debug(this.getName() + ":" +"submit " + dbgstr);
                    element.submit();
                    break;
                case selectByVisibleText:
                case selectByValue:
                    LOG.debug(this.getName() + ":" +"select " + action.setvalue + " for " + dbgstr);
                    Select dropdown = new Select(element);
                    if ( action.command.code == Schema.CmdType.selectByVisibleText )
                        dropdown.selectByVisibleText(action.setvalue);
                    else
                        dropdown.selectByValue(action.setvalue);
                    break;
                case openInNewTab:
                    LOG.debug(this.getName() + ":" +"openInNewTab " + dbgstr);
                    new Actions(driver).keyDown(Keys.CONTROL).click(element).keyUp(Keys.CONTROL).build().perform();
                    break;
                case sendKeys:
                    LOG.debug(this.getName() + ":" +"sendKeys " + action.setvalue + " to " + dbgstr);
                    element.sendKeys(action.setvalue);
                    break;
                case setPage:
                    LOG.debug(this.getName() + ":" +"setPage (sendKeys) " + index + " to " + dbgstr);
                    //element.clear(); element.sendKeys(String.valueOf(index));
                    new Actions(driver).clickAndHold(element).sendKeys(Keys.chord(Keys.CONTROL, "a"), String.valueOf(index)).build().perform();
                    break;
                case openInNewTab_ContextClick:
                    LOG.debug(this.getName() + ":" +"openInNewTab_ContextClick " + dbgstr);
                    /*
                    Actions a = new Actions(driver);
                    a.moveToElement(element);
                    a.contextClick(element);
                    try { Thread.sleep(100); } catch ( InterruptedException e ) { };
                    a.sendKeys(Keys.chord("t"));
                    */

                    try {
                        Actions oAction = new Actions(driver);
                        oAction.moveToElement(element);
                        Thread.sleep(100);
                        oAction.contextClick(element).build().perform();

                        Robot robot = new Robot();
                        robot.keyPress(KeyEvent.VK_T);
                        robot.keyRelease(KeyEvent.VK_T);
                    } catch ( AWTException e ) {
                        LOG.warn(this.getName() + ":" +"failed to create Robot for openInNewTab_ContextClick ", e);
                        return false;
                    } catch ( InterruptedException e ) {
                        LOG.warn(this.getName() + ":" +"InterruptedException ", e);
                        return false;
                    }

                    break;
                case executeScript:
                    LOG.debug(this.getName() + ":" +"executeScript " + dbgstr);
                    ((JavascriptExecutor)driver).executeScript(action.setvalue, element);
                    break;
                case moveToElement:
                    LOG.debug(this.getName() + ":" +"moveToElement " + dbgstr);
                    if( "input".equalsIgnoreCase(element.getTagName()) ){
                        element.sendKeys("");
                    } else{
                        new Actions(driver).moveToElement(element).perform();
                    }
                    //((JavascriptExecutor)driver).executeScript("arguments[0].focus();", element);
                    break;
                default: break;
            }
        }

        /* Expection handling */
        if ( action.expections == null ) {
            LOG.debug(this.getName() + ":" +"no Expection, go on");
        } else {
            for ( int iter = 0; iter < action.expections.expections.size(); iter++ ) {
                Schema.Expection expection = action.expections.expections.get(iter);
                if ( expection == null || expection.condition == null ) continue;
                if (expection.element != null)
                    LOG.debug(this.getName() + ":" +"Expection: " + expection.condition + " " + expection.element.element);
                else
                    LOG.debug(this.getName() + ":" +"Expection: " + expection.condition);

                try {
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
                        case "newWindowIsOpenedBackground":
                            try {
                                String newwindow = wait.until(newWindowIsOpened(currentWindowHandles));
                                if ( expection.condition.equals("newWindowIsOpened") ) {
                                    windows_stack.push(newwindow);
                                    driver.switchTo().window(newwindow);
                                    //String handle = driver.getWindowHandle();
                                    LOG.debug(this.getName() + ":" +"newWindowIsOpened: " + newwindow + " title:" + driver.getTitle());
                                } else {
                                    // stupid Baosteel bug which will open summary page in old window, but replace job list in new window
                                    String origwindow = (String)windows_stack.pop();
                                    windows_stack.push(newwindow);
                                    windows_stack.push(origwindow);
                                    LOG.debug(this.getName() + ":" +"newWindowIsOpened(but in background): " + newwindow + " title:" + driver.getTitle());
                                }
                            } catch (Exception e) {
                                LOG.warn(this.getName() + ":" +"Failed to open new window");
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
                                LOG.debug(this.getName() + ":" +"Yes, Alert Dialog appears");
                            } catch (Exception ee) {
                                LOG.warn(this.getName() + ":" +"Failed to see Alert Dialog");
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
                                LOG.debug(this.getName() + ":" +"onlywait 5 seconds");
                            }
                            break;
                        case "elementTextChanged":
                            String currentText = currentTexts.poll();
                            LOG.debug(this.getName() + ":" +"Current text: " + currentText);
                            String newtext = wait.until(elementTextChanged(wait_locator, currentText));
                            LOG.debug(this.getName() + ":" +"Element text changed to " + newtext);
                            break;
                        case "elementValueChanged":
                            String currentValue = currentTexts.poll();
                            LOG.debug(this.getName() + ":" +"Current value: " + currentValue);
                            String newvalue = wait.until(elementValueChanged(wait_locator, currentValue));
                            LOG.debug(this.getName() + ":" +"Element value changed to " + newvalue);
                            break;
                        case "elementToBeSelected":
                            wait.until(elementToBeSelected(wait_locator));
                            break;
                        case "wait":
                            int val = 10;
                            try {
                                val = Integer.parseInt(expection.value);
                                LOG.debug(this.getName() + ":" +"wait seconds " + expection.value);
                            } catch ( Exception e ) {
                                LOG.warn(this.getName() + ":" +"wait without valid value, wait 10 seconds");
                            }

                            //implicitlyWait looks works in asynchrous mode
                            //driver.manage().timeouts().implicitlyWait(val*1000, MILLISECONDS);
                            try {
                                Thread.sleep(val * 1000);
                            } catch ( Exception ee ) {}
                            LOG.debug(this.getName() + ":" +"waited seconds " + expection.value);
                            break;
                        default:
                            driver.manage().timeouts().implicitlyWait(10000, MILLISECONDS);
                            LOG.debug(this.getName() + ":" +"waited 10 seconds");
                            break;
                    }
                } catch ( Exception ee ) {
                    LOG.warn(this.getName() + ":" +"Specific expection failed, return false >>> Exception ", ee);
                    return false;
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
            LOG.debug(this.getName() + ":" +"No Actions, return");
            return true;
        }
        int original_windows = windows_stack.size();
        for ( int i = 0; i < actions.actions.size(); i++ ) {
            boolean ret = Action(xpath_prefix, index, actions.actions.get(i));
            if ( !ret ) {
                while ( windows_stack.size() > original_windows ) {
                    LOG.info(this.getName() + ":" +"close window opened by failed action before return false");
                    windows_stack.pop();
                    String previousWindowHandle = (String) windows_stack.peek();
                    driver.close();
                    driver.switchTo().window(previousWindowHandle);
                }
                return ret;
            }
        }
        return true;
    }

    private boolean Extracts(String xpath_prefix, int index, Schema.Extracts extracts, Job job){
        if ( extracts == null ) {
            LOG.debug(this.getName() + ":" +"Nothing to be extracted, return");
            return true;
        }
        if ( job == null) {
            LOG.warn(this.getName() + ":" +"Nont know where to save extracts, return");
            return false;
        }
        try {
            for (String key : extracts.items.keySet()) {
                Schema.Element ele = extracts.items.get(key);
                if ( (ele.how != null) && ele.how.equals("url") ) {
                    job.addField(Job.JOB_URL, driver.getCurrentUrl());
                    LOG.debug(this.getName() + ":" +"job_url" + ": " + driver.getCurrentUrl());
                } else if ( (ele.how != null) && ele.how.equals("int") ) {
                    int val = 0;
                    try {
                        val = Integer.parseInt(ele.value);
                    } catch (java.lang.Exception e) {}
                    job.addField(key, val);
                    LOG.debug(this.getName() + ":" +key + " : (int)" + ele.value);
                } else {
                    By locator = getLocator(xpath_prefix, index, ele);
                    String value = "";
                    Boolean formatted = false;
                    List<String> values = new ArrayList<String>();
                    List<WebElement> elements = locator.findElements(driver);

                    if ( elements.size() == 0 ) {
                        LOG.warn(this.getName() + ":" +"Failed to locate elements for extracing " + key);
                        return false;
                    } else {
                        LOG.debug(this.getName() + ":" +"Located " + elements.size() + " elements for extracing " + key);
                    }
                    for ( int i = 0; i < elements.size(); i++ ) {
                        if ( ele.method != null ) {
                            switch ( ele.method ) {
                                case "getAttribute":
                                    if ( ele.value != null ) {
                                        value = elements.get(i).getAttribute(ele.value);
                                    } else {
                                        value = elements.get(i).getAttribute("value");
                                    }
                                    break;
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
                        LOG.debug(this.getName() + ":" +" raw-> " + value);
                        if ( ele.transforms != null ) {
                            // Firstly handle transform against single item.
                            for ( int j = 0; j < ele.transforms.size(); j++ ) {
                                Schema.Transform transform = ele.transforms.get(j);
                                if ( transform == null || transform.how == null ) continue;
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
                                            LOG.warn(this.getName() + ":" +"regex faield" , me);
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
                                    case "regex_matcher":
                                        value = LocationUtils.match(value, transform.value, transform.which, transform.group);
                                        value = LocationUtils.format(value);
                                        formatted = true;
                                        break;
                                    case "tokenize":
                                        value = LocationUtils.tokenize(value);
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
                            if (transform == null || transform.how == null) continue;
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

                    LOG.debug(this.getName() + ":" +key + ":" + (value.length()>100?value.substring(0,100):value));

                    if ( !formatted ) {
                        // some default handling to avoid config item in schema
                        if (key.equals(Job.JOB_POST_DATE) || key.equals(Job.JOB_EXPIRE_DATE)) {
                            value = DateUtils.formatDate(value);
                        }
                        if (key.equals(Job.JOB_LOCATION)) {
                            value = LocationUtils.format(value);
                        }
                    }
                    job.addField(key, value);
                }
            }

        } catch ( Exception e ) {
            LOG.warn(this.getName() + ":" +"Extracts failed", e);
            return false;
        }
        return true;
    }
    private void PostProcessJob(final Job newjob, final Schema _schema) {
        // Generate unique job id
        JobUniqueIdCalc calc = _schema.job_unique_id_calc;
        if (calc != null && calc.how != null) {
            if (calc.how.equals("url_plus_title")) {
                newjob.addField(Job.JOB_UNIQUE_ID, (String)newjob.getField(Job.JOB_URL) + (String)newjob.getField(Job.JOB_TITLE));
            } else if (calc.how.equals("regex_on_url")) {
                if ( calc.value != null && !calc.value.isEmpty() ) {
                    try {
                        Perl5Util plutil = new Perl5Util();
                        String newurl = plutil.substitute(calc.value, (String)newjob.getField(Job.JOB_URL));
                        newjob.addField(Job.JOB_UNIQUE_ID, newurl);
                    } catch (MalformedPerl5PatternException me) {
                        LOG.warn(this.getName() + ":" +"Failed to generate unique id for job via regex " + calc.value);
                        newjob.addField(Job.JOB_UNIQUE_ID, newjob.getField(Job.JOB_URL));
                    }
                } else {
                    LOG.warn(this.getName() + ":" +"No regex to generate unique id for job");
                    newjob.addField(Job.JOB_UNIQUE_ID, newjob.getField(Job.JOB_URL));
                }
            }
        } else {
            newjob.addField(Job.JOB_UNIQUE_ID, newjob.getField(Job.JOB_URL));
        }

        if (!newjob.getFields().containsKey(Job.JOB_POST_DATE) ) {
            LOG.info(this.getName() + ":" +"No Job_date field extracted, use current time");
            newjob.addField(Job.JOB_POST_DATE, DateUtils.getCurrentDate());

            // here to check solr repository whether this item  exist already.
        }
    /*
        if ( newjob.getFields().containsKey(Job.JOB_SUB_COMPANY) ) {
            String subname = newjob.getField(Job.JOB_SUB_COMPANY);
            if (subname != null && !subname.isEmpty()) {
                newjob.addField(Job.JOB_COMPANY, subname);
            }

	    // This might be changed later,
	    // Now Fetcher will combine company name and subname together via Transforms,
	    // Finally we might need both fields to make client easier.
	    // which means to disable the combination in schema and comment this section of code
	    newjob.removeField(Job.JOB_SUB_COMPANY);
        }
    */
        // for potential future remove outdated jobs
        newjob.addField(Job.JOB_INDEX_DATE, DateUtils.getCurrentDate());

        if (!newjob.getFields().containsKey(Job.JOB_TYPE) ) {
            newjob.addField(Job.JOB_TYPE, 0);
        }

        if (!newjob.getFields().containsKey(Job.JOB_URL_TYPE) ) {
            newjob.addField(Job.JOB_URL_TYPE, 0);
        }

        if (!newjob.getFields().containsKey(Job.JOB_EXPIRED) ) {
            newjob.addField(Job.JOB_EXPIRED, false);
        }
    }

    private static int PROC_RESULT_FAIL = 0;
    private static int PROC_RESULT_OK = 1;
    private static int PROC_RESULT_OK_NDAYS = 2;
    private int Procedure(Schema.Procedure procedure, Job job) {
        if ( procedure == null ) return PROC_RESULT_OK;
        if ( procedure.loop_type == Schema.LOOP_TYPE.BEGIN ) {
            if ( ( procedure.loop_item_type==Schema.LOOP_ITEM_TYPE.PAGE )
                    || (procedure.loop_item_type == Schema.LOOP_ITEM_TYPE.OTHER) ) {
                LOG.debug(this.getName() + ":" +"Procedure: loop of BEGIN type for LOOP_ITEM_TYPE of " + (procedure.loop_item_type==Schema.LOOP_ITEM_TYPE.PAGE?"PAGE":"OTHER"));
                int loop_totalpages = 0;
                String xpath_prefix_loop = procedure.xpath_prefix_loop;
                if (xpath_prefix_loop != null && !xpath_prefix_loop.isEmpty()) {
                    By locator = By.xpath(xpath_prefix_loop);
                    List<WebElement> elements = locator.findElements(driver);
                    loop_totalpages = elements.size();
                    LOG.debug(this.getName() + ":" +"Procedure: loop of BEGIN type, find " + loop_totalpages + " pages with " + xpath_prefix_loop);
                } else if ( procedure.loop_totalpages != null ) {
                    By locator = getLocator(null, 0, procedure.loop_totalpages);
                    try {
                        WebElement element = locator.findElement(driver);
                        loop_totalpages = Integer.parseInt(element.getText());
                        LOG.debug(this.getName() + ":" +"Procedure: loop of BEGIN type, find " + loop_totalpages + " with loop_totalpages");
                    } catch ( NoSuchElementException e ) {
                        LOG.warn(this.getName() + ":" +"Procedure: loop of BEGIN type, loop_totalpages defined, but failed to parse valid number from this element");
                        return PROC_RESULT_FAIL;
                    }
                } else {
                    LOG.warn(this.getName() + ":" +"Procedure: loop of BEGIN type, but neither xpath_prefix nor loop_totalpages, cant continue");
                    return PROC_RESULT_FAIL;
                }

                if ( procedure.fetch_runtime_index == -1 ) procedure.fetch_runtime_index = procedure.begin_from;
                for (; procedure.fetch_runtime_index < loop_totalpages + procedure.end_to; procedure.fetch_runtime_index++) {
                    if ( procedure.loop_need_initial_action
                            || procedure.fetch_runtime_index != procedure.begin_from ) {
                        // Some site wont have the next page button, should use the for loop as well for page navigation
                        // we should not click the page Anchor in the first round, go to Procedure directly.
                        // But Volkswagen case: 2nd level page loop, need initial action to load job list
                        boolean result = true;
                        if ( procedure.loop_totalpages != null ) {
                            // via normal loop with index, in general get the toal pages firstly, then set "input" with index
                            result = Actions(null, procedure.fetch_runtime_index+1, procedure.actions);
                        } else {
                            // via the xpath prefix
                            String newprefix = procedure.xpath_prefix_loop + "[" + Integer.toString(procedure.fetch_runtime_index + 1) + "]/";
                            result = Actions(newprefix, 0, procedure.actions);
                        }
                        if ( ! result ) {
                            LOG.info(this.getName() + ":" +"Actions for going to next page failed, break");
                            break;
                        }
                    }
                    int res = Procedure(procedure.procedure, null);
                    if ( (res == PROC_RESULT_OK_NDAYS) ) {
                        if ( procedure.loop_item_type == Schema.LOOP_ITEM_TYPE.PAGE ) {
                            LOG.info(this.getName() + ":" +"Fetched jobs within " + fetch_n_days + " days, reach configured limit, return");
                            break;
                        } else {
                            LOG.debug(this.getName() + ":" +"Fetched jobs within " + fetch_n_days + " days, but continue(LOOP_ITEM_TYPE.OTHER)");
                        }
                    }
                    if ( _schema.fetch_total_jobs == fetch_n_jobs ) {
                        LOG.info(this.getName() + ":" +"Fetched " + fetch_n_jobs + " jobs, reach configured limit, return");
                        break;
                    } else if ((procedure.fetch_runtime_index-procedure.begin_from+1) >= fetch_n_pages) {
                        LOG.info(this.getName() + ":" +"Fetched " + fetch_n_pages + " pages, reach configured limit, return");
                        break;
                    }
                }

                if ( loop_totalpages == 0 ) {
                    // Made page loop as optional, Mainly for Volkswagen case
                    // where maximum 2 levels of pages loop, but one of them maybe absent.
                    LOG.info(this.getName() + ":" +"Zero element found for page loop, go forward to inner procedure");
                    Procedure(procedure.procedure, null);
                }
            } else {
                LOG.debug(this.getName() + ":" +"Procedure: loop of BEGIN type for job list");
                String xpath_prefix_loop = procedure.xpath_prefix_loop;
                if (xpath_prefix_loop != null && !xpath_prefix_loop.isEmpty()) {
                    By locator = By.xpath(xpath_prefix_loop);
                    List<WebElement> elements = locator.findElements(driver);
                    LOG.debug(this.getName() + ":" +"Procedure: loop of BEGIN type, find " + elements.size() + " jobs with " + xpath_prefix_loop);
                    if ( procedure.fetch_runtime_index == -1 ) procedure.fetch_runtime_index = procedure.begin_from;
                    boolean hit_outdated_jobs = false;
                    for (; procedure.fetch_runtime_index < elements.size() + procedure.end_to; procedure.fetch_runtime_index++) {
                        final Job newjob = new Job();
                        newjob.addField(Job.JOB_COMPANY, CompanyUtils.getDisplayName(_schema.getName()));
                        String newprefix = xpath_prefix_loop + "[" + Integer.toString(procedure.fetch_runtime_index + 1) + "]/";
                        if (!Extracts(newprefix, procedure.fetch_runtime_index + 1, procedure.extracts, newjob)) {
                            LOG.warn(this.getName() + ":" +"Failed to extract info for this job, ignore");
                            continue;
                        }

                        if (DateUtils.nDaysAgo((String)newjob.getField(Job.JOB_POST_DATE), fetch_n_days)) {
                            LOG.debug(this.getName() + ":" +"Job older than configured date, ignore");
                            // current page list already has some jobs older than configured date,
                            // but will continue fetching all the jobs in current page, because some company will use reverse order
                            // just set a tmp flag here, and will return PROC_RESULT_OK_NDAYS, wont continue try next page any more.
                            hit_outdated_jobs = true;
                            continue;
                        }
                        if ( !Actions(newprefix, 0, procedure.actions) ) {
                            LOG.warn(this.getName() + ":" +"Action for job failed, ignore");
                            continue;
                        }

                        int res = Procedure(procedure.procedure, newjob);
                        if ( res == PROC_RESULT_FAIL ) {
                            LOG.warn(this.getName() + ":" +"Procedure for single job failed, ignore");
                            continue;
                        } else if ( res == PROC_RESULT_OK_NDAYS ) {
                            LOG.debug(this.getName() + ":" +"Job older than configured date(detected in job detail page), ignore");
                            hit_outdated_jobs = true;
                            continue;
                        }

                        PostProcessJob(newjob, _schema);

                        _joblist.addJob(newjob);

                        _schema.fetch_total_jobs++;

                        if ( (_schema.fetch_total_jobs == fetch_n_jobs)
                        || ((procedure.fetch_runtime_index-procedure.begin_from+1) >= fetch_n_jobs_perpage) ) {
                            if ((procedure.fetch_runtime_index - procedure.begin_from + 1) >= fetch_n_jobs_perpage) {
                                LOG.info(this.getName() + ":" +"Fetched " + fetch_n_jobs_perpage + " jobs perpage, reach configured limit, return");
                                procedure.fetch_runtime_index = -1;
                            }
                            if (_schema.fetch_total_jobs == fetch_n_jobs) {
                                LOG.info(this.getName() + ":" +"Fetched " + fetch_n_jobs + " jobs, reach configured limit, return");
                            }
                            break;
                        }
                    }
                    if ( procedure.fetch_runtime_index == elements.size() + procedure.end_to ) {
                        procedure.fetch_runtime_index = -1;
                    }
                    if ( hit_outdated_jobs )
                        return PROC_RESULT_OK_NDAYS; // won't continue next page.
                    else
                        return PROC_RESULT_OK; // will continue next page.
                } else {
                    LOG.warn(this.getName() + ":" +"Procedure: loop of BEGIN type, dont have xpath_prefix");
                    return PROC_RESULT_FAIL;
                }
            }
        } else if ( procedure.loop_type == Schema.LOOP_TYPE.END ) {
            LOG.debug(this.getName() + ":" +"Procedure: loop of END type for page list");
            boolean result = true;
            if ( procedure.fetch_runtime_index != -1 ) {
                int tmppages = 0;
                do {
                    // move forward to the pages failed last time.
                } while ( (tmppages++ < procedure.fetch_runtime_index) && (result=Actions(null, 0, procedure.actions)) );
                if ( !result ) {
                    LOG.warn(this.getName() + ":" +"Failed again during moving to the failed place last time");
                    return PROC_RESULT_FAIL;
                }
            } else {
                procedure.fetch_runtime_index = 0;
            }

            int res;
            do {
                res = Procedure(procedure.procedure, null);
                if ( res == PROC_RESULT_OK_NDAYS ) break;
            } while ( (++procedure.fetch_runtime_index<fetch_n_pages) && (_schema.fetch_total_jobs<fetch_n_jobs) && (result=Actions(null, 0, procedure.actions)) );
            if ( res == PROC_RESULT_OK_NDAYS ) {
                LOG.info(this.getName() + ":" +"Fetched jobs within configured " + fetch_n_days + " days, break");
                return PROC_RESULT_OK;
            } else if ( !result ) {
                LOG.warn(this.getName() + ":" +"Actions for going to next page failed, break");
                return PROC_RESULT_FAIL;
            } else if ( _schema.fetch_total_jobs == fetch_n_jobs ) {
                LOG.info(this.getName() + ":" +"Fetched " + fetch_n_jobs + " jobs, reach configured limit, return");
                return PROC_RESULT_OK;
            } else {
                LOG.info(this.getName() + ":" +"Fetched " + fetch_n_pages + " pages, reach configured limit, return");
                return PROC_RESULT_OK;
            }
        } else {
            LOG.debug(this.getName() + ":" +"Procedure for single job");
            int res = PROC_RESULT_OK;
            if ( !Extracts(null, 0, procedure.extracts, job) ) {
                LOG.info(this.getName() + ":" +"Failed to extract info for this job (summary page), ignore");
                res = PROC_RESULT_FAIL;
            }
            if (DateUtils.nDaysAgo((String)job.getField(Job.JOB_POST_DATE), fetch_n_days)) {
                LOG.info(this.getName() + ":" +"Job older than configured date (summary page), ignore");
                res = PROC_RESULT_OK_NDAYS;
            }
            Actions(null, 0, procedure.actions);
            // can't return false directly, there will be 'restore/close window' action

            return res;
        }
        return PROC_RESULT_OK;
    }
}
