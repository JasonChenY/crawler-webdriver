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


import java.io.File;
import java.lang.Thread;
import java.util.Map;
import java.util.HashMap;
import java.util.Dictionary;
import java.util.Hashtable;
import java.io.BufferedReader; 
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class WebDriverService {
    private static ChromeDriverService service = null;
    public static void CreateAndStartService(String driver, int port) throws Exception {
        if ( service == null ) {
            service = new ChromeDriverService.Builder()
                    .usingDriverExecutable(new File(driver))
                            //.usingAnyFreePort()
                    .usingPort(port)
                    .build();
            service.start();
        }
    }

    public static void StopService() {
        if ( service != null ) service.stop();
    }

    public static WebDriver getWebDriver(String url, String dir, String proxyIpAndPort) throws Exception {
        DesiredCapabilities capabilities = DesiredCapabilities.chrome();
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

        if ( proxyIpAndPort != null ) {
            org.openqa.selenium.Proxy proxy = new org.openqa.selenium.Proxy();
            proxy.setHttpProxy(proxyIpAndPort).setFtpProxy(proxyIpAndPort).setSslProxy(proxyIpAndPort);
            capabilities.setCapability(CapabilityType.ForSeleniumServer.AVOIDING_PROXY, true);
            capabilities.setCapability(CapabilityType.ForSeleniumServer.ONLY_PROXYING_SELENIUM_TRAFFIC, true);
            System.setProperty("http.nonProxyHosts", "localhost");
            capabilities.setCapability(CapabilityType.PROXY, proxy);
        }

        return new RemoteWebDriver(service.getUrl()/*new java.net.URL(url)*/, capabilities);
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
            driver = getWebDriver("http://127.0.0.1:8899", download_directory + "/" + subdir, null);
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
            CreateAndStartService("/sdk/tools/jobs/webdriver/lib/chromedriver", 8899);
        } catch ( Exception e ) {
            e.printStackTrace();
        }
       
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

        try {
             Thread.sleep(1000000); 
        } catch ( Exception ee ) {
             ee.printStackTrace();
        }

        StopService();
    } 
}
