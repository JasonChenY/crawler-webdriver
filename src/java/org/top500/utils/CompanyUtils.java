package org.top500.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Properties;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class CompanyUtils {
    public static final Logger LOG = LoggerFactory.getLogger(CompanyUtils.class);
    private static Map<String, String> COMPANYS_MAP = new HashMap<String, String>();
    private static Properties prop = new Properties();
    static {
        LOG.info("Initializing company names");
        try {
            InputStream input = LocationUtils.class.getClassLoader().getResourceAsStream("companyname.txt");
            BufferedReader bf = new BufferedReader(new InputStreamReader(input));
            prop.load(bf);
            bf.close();
            Enumeration<?> keys = prop.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                COMPANYS_MAP.put(key.toUpperCase(), prop.getProperty(key));
            }
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.toString(), e);
            }
        }
        //    return COMPANYS_MAP;
    }
    private CompanyUtils() {}
    public static String getDisplayName(String name) {
        if ( name != null ) {
            String value = COMPANYS_MAP.get(name.toUpperCase());
            return (value != null)?value:"unknown";
        } else {
            return "unknown";
        }
    }
}
