package org.top500.utils;

import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.io.IOException;

public class Configuration {
    Properties prop;
    static Configuration instance = null;

    public static Configuration getInstance() {
        if ( instance == null ) {
            instance = new Configuration();
        }
        return instance;
    }

    private Configuration() {
        prop = new Properties();
        try {
            //load a properties file from class path, inside static method
            prop.load(Configuration.class.getClassLoader().getResourceAsStream("config.properties"));
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public boolean getBoolean(String key, boolean def) {
        if ( prop.getProperty(key) != null ) {
            return Boolean.parseBoolean(prop.getProperty(key));
        } else
            return def;
    }

    public int getInt(String key, int def) {
        if ( prop.getProperty(key) != null ) {
            return Integer.parseInt(prop.getProperty(key));
        } else
            return def;
    }

    public String get(String key) {
        return prop.getProperty(key);
    }

    public String get(String key, String def) {
        return prop.getProperty(key, def);
    }
}