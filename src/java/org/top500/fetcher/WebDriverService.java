package org.top500.fetcher;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.CapabilityType;

import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedCondition;

import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;

import java.io.File;
import java.lang.Integer;
import java.lang.Thread;
import java.util.Map;
import java.util.HashMap;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.ArrayList;
import java.io.BufferedReader; 
import java.io.FileInputStream;
import java.io.InputStreamReader;

import org.top500.utils.Configuration;

public class WebDriverService {
    private static ChromeDriverService chromeDriverService = null;
    private static PhantomJSDriverService phantomJSDriverService = null;
    public static void CreateAndStartService() throws Exception {
        Configuration _conf = Configuration.getInstance();
        // comment fetch.webdriver.chrome.exec to disable chromedriver on linux server
        if ( (chromeDriverService == null) && (_conf.get("fetch.webdriver.chrome.exec") != null) ) {
            chromeDriverService = new ChromeDriverService.Builder()
                    .usingDriverExecutable(new File(_conf.get("fetch.webdriver.chrome.exec", "lib/chromedriver")))
                            //.usingAnyFreePort()
                    .usingPort(_conf.getInt("fetch.webdriver.chrome.port", 8899))
                    .build();
            chromeDriverService.start();
        }
        if ( (phantomJSDriverService == null) ) { 
            String args[] = {
                "--web-security=false",
                "--ssl-protocol=any",
                "--ignore-ssl-errors=true",
                "--local-to-remote-url-access=true",
                "--disk-cache=true"
            };
            phantomJSDriverService = new PhantomJSDriverService.Builder()
                    .usingPhantomJSExecutable(new File(_conf.get("fetch.webdriver.phantomjs.exec", "lib/phantomjs")))
                    //.usingGhostDriver(new File("ghostDriverfile"))
                    .usingPort(_conf.getInt("fetch.webdriver.phantomjs.port", 8898))
                    .usingCommandLineArguments(args)
                    .build();
            phantomJSDriverService.start();
        }
    }

    public static void StopService() {
        if ( chromeDriverService != null ) chromeDriverService.stop();
        if ( phantomJSDriverService != null ) phantomJSDriverService.stop();
    }

    public static enum DRIVER_TYPE {CHROME, PHANTOMJS, LOCAL_CHROME, LOCAL_PHANTOMJS};
    public static WebDriver getWebDriver(DRIVER_TYPE type, String dir, String proxyIpAndPort) throws Exception {
        Configuration _conf = Configuration.getInstance();
        DesiredCapabilities capabilities = null;
        String url = "";
        if ( type == DRIVER_TYPE.CHROME || type == DRIVER_TYPE.LOCAL_CHROME ) {
            capabilities = DesiredCapabilities.chrome();

            capabilities.setCapability("chrome.switches", "disable-images");// to disable image showing

            ChromeOptions options = new ChromeOptions();
            //if specify this, all threads will share same session, quit will close all the window
            //options.addArguments("user-data-dir=/sdk/tmp/chrome/profile");
            options.addArguments("start-maximized");

            Map<String, Object> prefs = new HashMap<String, Object>();
            prefs.put("profile.default_content_settings.popups", 0);
            prefs.put("download.default_directory", dir);
            prefs.put("savefile.type", 0);
            options.setExperimentalOption("prefs", prefs);

            capabilities.setCapability(ChromeOptions.CAPABILITY, options);

            url = ((type==DRIVER_TYPE.CHROME)?_conf.get("fetch.webdriver.chrome.host", "http://localhost"):"http://localhost") + ":"
                    + Integer.toString(_conf.getInt("fetch.webdriver.chrome.port", 8899));

        } else if ( type == DRIVER_TYPE.PHANTOMJS || type == DRIVER_TYPE.LOCAL_PHANTOMJS ) {
            capabilities = DesiredCapabilities.phantomjs();
            capabilities.setJavascriptEnabled(true);
            capabilities.setCapability("takesScreenshot", true);
            capabilities.setCapability("loadImages",false);

            ArrayList<String> cliArgsCap = new ArrayList<String>();
            cliArgsCap.add("--web-security=false");
            cliArgsCap.add("--ssl-protocol=any");
            cliArgsCap.add("--ignore-ssl-errors=true");
            cliArgsCap.add("--local-to-remote-url-access=true");
            cliArgsCap.add("--disk-cache=true");
/*
            cliArgsCap.add("--handlesAlerts=true");
            cliArgsCap.add("--databaseEnabled=true");
            cliArgsCap.add("--locationContextEnabled=true");
            cliArgsCap.add("--applicationCacheEnabled=true");
            cliArgsCap.add("--browserConnectionEnabled=true");
            cliArgsCap.add("--cssSelectorsEnabled=true");
            cliArgsCap.add("--webStorageEnabled=true");
            cliArgsCap.add("--rotatable=true");
*/
            capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS, cliArgsCap);

            // Control LogLevel for GhostDriver, via CLI arguments
            capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_GHOSTDRIVER_CLI_ARGS, new String[] {"--logLevel=DEBUG"});

            capabilities.setBrowserName("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:51.0) Gecko/20100101 Firefox/51.0");

            url = ((type==DRIVER_TYPE.PHANTOMJS)?_conf.get("fetch.webdriver.phantomjs.host", "http://localhost"):"http://localhost") + ":"
                    + Integer.toString(_conf.getInt("fetch.webdriver.phantomjs.port", 8898));
        }

        if ( proxyIpAndPort != null ) {
            org.openqa.selenium.Proxy proxy = new org.openqa.selenium.Proxy();
            proxy.setHttpProxy(proxyIpAndPort).setFtpProxy(proxyIpAndPort).setSslProxy(proxyIpAndPort);
            capabilities.setCapability(CapabilityType.ForSeleniumServer.AVOIDING_PROXY, true);
            capabilities.setCapability(CapabilityType.ForSeleniumServer.ONLY_PROXYING_SELENIUM_TRAFFIC, true);
            System.setProperty("http.nonProxyHosts", "localhost");
            capabilities.setCapability(CapabilityType.PROXY, proxy);
        }
        System.out.println(url);
        return new RemoteWebDriver(new java.net.URL(url), capabilities);
    }

    public static File lastFileModified(String dir) {
        File fl = new File(dir);
        File[] files = fl.listFiles(new java.io.FileFilter() {          
            public boolean accept(File file) {
                return file.isFile();
            }
            });
        long lastMod = Long.MIN_VALUE;
        File choice = null;
        for (File file : files) {
            if (file.lastModified() > lastMod) {
                choice = file;
                lastMod = file.lastModified();
            }
        }
        return choice;
    }

    private static class DownloadThread extends Thread{
        private String url;
        private String subdir;
        private String download_directory = "/sdk/tmp/chrome/download";
        public DownloadThread(String url) {
            this.url = url;
        }
        public void run(){
            WebDriver driver = null;
            try {
                subdir = Long.toString(Thread.currentThread().getId());
                driver = getWebDriver(DRIVER_TYPE.PHANTOMJS, download_directory + "/" + subdir, null);
                driver.get(url);
                System.out.println("Page Title: " +  driver.getTitle());
                Thread.sleep(10000);
                System.out.println("Fetched file: " + lastFileModified(download_directory + "/" + subdir).getName());
            } catch ( Exception e ) {
                System.out.println("Failed to donwload" + url);
                e.printStackTrace();
            } finally {
                driver.quit();
            }
        }
    }
    
    public static void main(String[] args) { 
        try {
            Configuration conf = Configuration.getInstance();
            CreateAndStartService();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
/*
        try
        {
            InputStreamReader in=new InputStreamReader(new FileInputStream("/sdk/tmp/webapps/Bayer/url_list.data"));
            BufferedReader br=new BufferedReader(in);
            String url;
            while ((url = br.readLine()) != null) {
                System.out.println("Fetching " + url);
                DownloadThread dt = new DownloadThread(url);
                dt.start();
                Thread.sleep(10000);
            }
        } catch(Exception e){

        }
*/
        try {
             Thread.sleep(1000000000);
        } catch ( Exception ee ) {
             ee.printStackTrace();
        }

        StopService();
    } 
}
