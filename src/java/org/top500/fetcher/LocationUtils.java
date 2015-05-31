package org.top500.fetcher;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.oro.text.perl.MalformedPerl5PatternException;
import org.apache.oro.text.perl.Perl5Util;

import java.util.StringTokenizer;
import java.util.Properties;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader; 

/* Purpose for this class:
 * - some company use json file, json-path is not powerful in string operation
 *   we need do some string operation via Perl5 regex to get the clean information
 * - different company use different city name, w/o "shi", this can be done in solr
 *   but it is better to commit same format toward solr in the very beginnng phase
 * - chinese and english city name as well.
 */
public class LocationUtils {
    public static final Logger LOG = LoggerFactory.getLogger(LocationUtils.class);
    private static List<NameValuePair> replaces = initialize_replaces();
    private static List<NameValuePair> initialize_replaces() {
        List<NameValuePair> replaces = new ArrayList<NameValuePair>();

        try {
            /* it is fortunate that all the china city won't have this word in base name */
            replaces.add(new NameValuePair(new String("市".getBytes(), "UTF-8"), ""));
            replaces.add(new NameValuePair(new String("'".getBytes(), "UTF-8"), ""));
            /* is it a good idea to translate all english to chinese city name here
             * will be too slow, need find a better way, find tokenized word from hash?
             */
        } catch (Exception e) {
        }
        return replaces;
    }

    private static Map<String, String> CITIES_MAP = new HashMap<String, String>();
    static {
    //private static Map<String, String> CITIES_MAP = initialize_cities();
    //private static Map<String, String> initialize_cities() {
    //    Map<String, String> CITIES_MAP = new HashMap<String, String>();
        LOG.info("Initializing cities");
        try {
            Properties p = new Properties();
            //p.load(LocationUtils.class.getResourceAsStream("cities.txt"));
            //p.load(fis);
            //fis.close();
            InputStream input = LocationUtils.class.getClassLoader().getResourceAsStream("cities.txt");
            BufferedReader bf = new BufferedReader(new InputStreamReader(input));
            //FileInputStream fis = new FileInputStream("cities.txt");
            //BufferedReader bf = new BufferedReader(new InputStreamReader(fis));
            p.load(bf); 
            bf.close();
            Enumeration<?> keys = p.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                String[] values = p.getProperty(key).split(",", -1);
                for (int i = 0; i < values.length; i++) {
                    CITIES_MAP.put(values[i].trim().toLowerCase(), new String(key.getBytes(), "UTF-8"));
                    if (LOG.isDebugEnabled()) LOG.debug("Adding " + values[i] + "  " + key);
                }
            }
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.toString(), e);
            }
        }
    //    return CITIES_MAP;
    }

    private static String format(String str) {
        /* Microsoft use some chinese word */
        for ( int i = 0; i < replaces.size(); i++ ) {
            str = str.replaceAll(replaces.get(i).getName(), replaces.get(i).getValue());
        }

        StringBuilder result = new StringBuilder();
        StringTokenizer tokenizer = new StringTokenizer(str, ",");
        while (tokenizer.hasMoreTokens()) {
            String actualToken = tokenizer.nextToken();
            String out = CITIES_MAP.get(actualToken.trim().toLowerCase());
            if (out != null) {
                result.append(out);
                if (LOG.isDebugEnabled()) LOG.debug("Translating " + actualToken + "  " + out);
            } else {
                result.append(actualToken);
            }
            if ( tokenizer.hasMoreTokens() ) result.append(",");
        }
        return result.toString();
    }

    public static String format(String d, String regex) {
        d = d.trim();
        //d = SolrUtils.stripNonCharCodepoints(d);
        if ( regex != null && !regex.isEmpty() ){
            try {
                Perl5Util plutil = new Perl5Util();
                d = plutil.substitute(regex, d);
            } catch (MalformedPerl5PatternException me ) {
                LOG.warn("location " + d + " cant be matched with " + regex);
            }
        }
        return format(d);
    }

    public static void main(String args[]) {
        String    strs[] = {"hong kong", "上海市/南京市",  "中国 - 江苏 - 南京市 ", 
                  "中国 / 江苏 / 南京市 ",                 "中国 - 江苏 - 南京市  ",};
        String formats[] = {"",          "" ,              "s/[^-]+-[^-]+-\\s*(\\S*)/$1/g", 
                  "s/[^\\/]+\\/[^\\/]+\\/\\s*(\\S*)/$1/g", "s/(.*-)*\\s*(.*)(\\s*-\\s*)*$/$2/g", };

        /* Huawei */
        String reg = "s/(.*-)*\\s*([^-]+)(\\s*-\\s*)?$/$2/g";
        String tests[] = {"中国-江苏-南京市", "中国-江苏-", " 中国 - 江苏 - 南京市 ", " 中国 - 江苏 - "};
        try {
            for ( int i = 0; i < strs.length; i++ ) {
                LOG.debug(strs[i] + " : " + format(strs[i], formats[i]));
            }
            for ( int i = 0; i < tests.length; i++ ) {
                LOG.debug(tests[i] + " : " + format(tests[i], reg));
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        /* Honeywell */
        reg = "s/([^-,]*).*/$1/g";
        String tests2[] = {"Nanjing-Jiangsu-China", "China", "Shanghai,China"};
        try {
            for ( int i = 0; i < tests2.length; i++ ) {
                LOG.debug(tests2[i] + " : " + format(tests2[i], reg));
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        /* Google */
        System.out.println("=======");
        reg = "s/\\s*([^,]*)[^;]*;?/$1,/g";
        String test3[] = {"Beijing",
                          "Hong Kong",
                          "Taipei, Taiwan",
                          "Beijing, Beijing, China; Shanghai, Shanghai, China",
                          "Beijing, Beijing, China; Shanghai, Shanghai, China; Taipei, Taiwan; Hong Kong"};
        try {
            for ( int i = 0; i < test3.length; i++ ) {
                LOG.debug(test3[i] + " : " + format(test3[i], reg));
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}
