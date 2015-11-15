package org.top500.utils;

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

import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.PatternMatcherInput;
import org.apache.oro.text.regex.Perl5Matcher;
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
    private static Properties prop = new Properties();
    static {
        //private static Map<String, String> CITIES_MAP = initialize_cities();
        //private static Map<String, String> initialize_cities() {
        //    Map<String, String> CITIES_MAP = new HashMap<String, String>();
        LOG.info("Initializing cities");
        try {
            //p.load(LocationUtils.class.getResourceAsStream("cities.txt"));
            //p.load(fis);
            //fis.close();
            InputStream input = LocationUtils.class.getClassLoader().getResourceAsStream("cities.txt");
            BufferedReader bf = new BufferedReader(new InputStreamReader(input));
            //FileInputStream fis = new FileInputStream("cities.txt");
            //BufferedReader bf = new BufferedReader(new InputStreamReader(fis));
            prop.load(bf);
            bf.close();
            Enumeration<?> keys = prop.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                String[] values = prop.getProperty(key).split(",", -1);
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

    public static String format(String str) {
        /* Microsoft use some chinese word */
        for ( int i = 0; i < replaces.size(); i++ ) {
            str = str.replaceAll(replaces.get(i).getName(), replaces.get(i).getValue());
        }

        StringBuilder result = new StringBuilder();
        StringTokenizer tokenizer = new StringTokenizer(str, ",");
        boolean hitunknown = false;
        while (tokenizer.hasMoreTokens()) {
            String actualToken = tokenizer.nextToken();
            String out = CITIES_MAP.get(actualToken.trim().toLowerCase());
            if (out != null) {
                result.append(out);
                if (LOG.isDebugEnabled()) LOG.debug("Translating " + actualToken + "  " + out);
            } else {
                //result.append(actualToken);
                hitunknown = true;
            }
            if ( tokenizer.hasMoreTokens() ) result.append(",");
        }
        if ( hitunknown && (result.length()==0) ) result.append("其他");
        return result.toString();
    }

    public static String format(String d, String regex) {
        d = d.trim();
        if ( d.isEmpty() ) return "其他";
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

    public static String match(String input, String regex, int which, int grp) {
        /* grp:   ( 1, 2, 3 )
        *         Only for extract one specific group id, items separated by ','
        *  which: indicate which mater to be extracted ( 1, 2,  3 )
        *         0: all
        *         -1: last
        **/
        StringBuffer result = new StringBuffer();
        Perl5Util plUtil = new Perl5Util();
        PatternMatcherInput matcherInput = new PatternMatcherInput(input);
        boolean firsttime = true;
        int order = 0;
        String item="";
        while (plUtil.match(regex, matcherInput)) {
            order++;
            MatchResult matchresult = plUtil.getMatch();
            item =matchresult.group(grp);
            if ( (which == 0) || (order == which) ) {
                if (item != null) {
                    if (!firsttime) result.append(",");
                    result.append(item);
                    firsttime = false;
                }
            }
        }
        // only interested in last matched part
        if ( which == -1 ) {
            if (!firsttime) result.append(",");
            result.append(item);
        }
        return result.toString();
    }
    public static String tokenize(String d) {
        StringBuilder result = new StringBuilder();
        try {
            Perl5Util plutil = new Perl5Util();
            d = plutil.substitute("s/\\s*\\/\\s*/,/g", d);
            d = plutil.substitute("s/\\s*or\\s*/,/g", d);
            d = plutil.substitute("s/\\s*\\(\\s*/,/g", d);
            d = plutil.substitute("s/\\s*\\)\\s*/,/g", d);
            d = plutil.substitute("s/\\s*;\\s*/,/g", d);
            d = plutil.substitute("s/\\s*-\\s*/,/g", d);
            //d = plutil.substitute("s/\\s+/,/g", d);
        } catch ( Exception e ) {}

        boolean first = true;
        Map<String,Object> tmpmap = new HashMap<String,Object>();
        StringTokenizer tokenizer = new StringTokenizer(d, ",");
        while (tokenizer.hasMoreTokens()) {
            String actualToken = tokenizer.nextToken().trim().toLowerCase();
            String out = CITIES_MAP.get(actualToken);
            if (out != null) {
                tmpmap.put(out, null);
            } else if ( prop.getProperty(actualToken) != null ) {
                tmpmap.put(actualToken, null);
            }
        }
        if (tmpmap.entrySet().isEmpty()) {
            result.append("其他");
        } else {
            for (String key : tmpmap.keySet()) {
                if (first) {
                    result.append(key);
                    first = false;
                } else {
                    result.append("," + key);
                }
            }
        }
        return result.toString();
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
        } catch (Exception e) {
            e.printStackTrace();
        }

        /* Sanofi */
        System.out.println("=======");
        reg = "/([^-]*)市/";
        String test4[] = {"上海市-静安区",
                "德州市-乐陵市",
                "上海市-静安区",
                "北京-北京市",
                "吕梁市-孝义市",
                "贵州-贵阳市"};
        try {
            for ( int i = 0; i < test4.length; i++ ) {
                LOG.debug(test4[i] + " : " + match(test4[i], reg, 1, 1));
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        System.out.println("=======");
        String test5[] = {
                "Africa, Asia, Australasia-CN-Jiangsu-Taizhou",
                "Africa, Asia, Australasia-China",
                "Africa, Asia, Australasia-CN-Shanghai-Shanghai, Africa, Asia, Australasia-CN-Jiangsu-Taizhou"
        };
        try {
            for ( int i = 0; i < test5.length; i++ ) {
                System.out.println(test5[i] + " : " + tokenize(test5[i]));
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}
