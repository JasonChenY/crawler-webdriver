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

/** Borrowed from solr
 * This class has some code from HttpClient DateUtil.
 */
public class DateUtils {
    //start HttpClient
    /**
     * Date format pattern used to parse HTTP date headers in RFC 1123 format.
     */
    public static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

    /**
     * Date format pattern used to parse HTTP date headers in RFC 1036 format.
     */
    public static final String PATTERN_RFC1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";

    /**
     * Date format pattern used to parse HTTP date headers in ANSI C
     * <code>asctime()</code> format.
     */
    public static final String PATTERN_ASCTIME = "EEE MMM d HH:mm:ss yyyy";
    //These are included for back compat
    private static final Collection<String> DEFAULT_HTTP_CLIENT_PATTERNS = Arrays.asList(
            PATTERN_ASCTIME, PATTERN_RFC1036, PATTERN_RFC1123);

    private static final Date DEFAULT_TWO_DIGIT_YEAR_START;

    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ROOT);
        calendar.set(2000, Calendar.JANUARY, 1, 0, 0);
        DEFAULT_TWO_DIGIT_YEAR_START = calendar.getTime();
    }

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    //end HttpClient

    //---------------------------------------------------------------------------------------

    /**
     * A suite of default date formats that can be parsed, and thus transformed to the Solr specific format
     */
    public static final Collection<String> DEFAULT_DATE_FORMATS = new ArrayList<String>();

    static {
        DEFAULT_DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ss'Z'");
        DEFAULT_DATE_FORMATS.add("yyyy-MM-dd'T'HH:mm:ss");
        DEFAULT_DATE_FORMATS.add("yyyy-MM-dd");
        DEFAULT_DATE_FORMATS.add("yyyy-MM-dd hh:mm:ss");
        DEFAULT_DATE_FORMATS.add("yyyy-MM-dd HH:mm:ss");
        DEFAULT_DATE_FORMATS.add("EEE MMM d hh:mm:ss z yyyy");
        DEFAULT_DATE_FORMATS.add("dd-MMM-yyyy"); /* for IBM */
        DEFAULT_DATE_FORMATS.add("dd MMM yyyy"); /* for Microsoft */
        DEFAULT_DATE_FORMATS.add("MM/dd/yyyy"); /* for Danone */
        DEFAULT_DATE_FORMATS.addAll(DEFAULT_HTTP_CLIENT_PATTERNS);
    }

    /**
     * Returns a formatter that can be use by the current thread if needed to
     * convert Date objects to the Internal representation.
     *
     * @param d The input date to parse
     * @return The parsed {@link java.util.Date}
     * @throws java.text.ParseException If the input can't be parsed
     */
    public static Date parseDate(String d) throws ParseException {
        return parseDate(d, DEFAULT_DATE_FORMATS);
    }


    public static Date parseDate(String d, Collection<String> fmts) throws ParseException {
        // 2007-04-26T08:05:04Z
        if (d.endsWith("Z") && d.length() > 20) {
            return getThreadLocalDateFormat().parse(d);
        }
        return parseDate(d, fmts, null);
    }

    /**
     * Slightly modified from org.apache.commons.httpclient.util.DateUtil.parseDate
     * <p/>
     * Parses the date value using the given date formats.
     *
     * @param dateValue   the date value to parse
     * @param dateFormats the date formats to use
     * @param startDate   During parsing, two digit years will be placed in the range
     *                    <code>startDate</code> to <code>startDate + 100 years</code>. This value may
     *                    be <code>null</code>. When <code>null</code> is given as a parameter, year
     *                    <code>2000</code> will be used.
     * @return the parsed date
     * @throws ParseException if none of the dataFormats could parse the dateValue
     */
    public static Date parseDate(
            String dateValue,
            Collection<String> dateFormats,
            Date startDate
    ) throws ParseException {

        if (dateValue == null) {
            throw new IllegalArgumentException("dateValue is null");
        }
        if (dateFormats == null) {
            dateFormats = DEFAULT_HTTP_CLIENT_PATTERNS;
        }
        if (startDate == null) {
            startDate = DEFAULT_TWO_DIGIT_YEAR_START;
        }
        // trim single quotes around date if present
        // see issue #5279
        if (dateValue.length() > 1
                && dateValue.startsWith("'")
                && dateValue.endsWith("'")
                ) {
            dateValue = dateValue.substring(1, dateValue.length() - 1);
        }

        SimpleDateFormat dateParser = null;
        Iterator formatIter = dateFormats.iterator();

        while (formatIter.hasNext()) {
            String format = (String) formatIter.next();
            if (dateParser == null) {
                dateParser = new SimpleDateFormat(format, Locale.ROOT);
                dateParser.setTimeZone(GMT);
                dateParser.set2DigitYearStart(startDate);
            } else {
                dateParser.applyPattern(format);
            }
            try {
                return dateParser.parse(dateValue);
            } catch (ParseException pe) {
                // ignore this exception, we will try the next format
            }
        }

        // we were unable to parse the date
        throw new ParseException("Unable to parse the date " + dateValue, 0);
    }


    /**
     * Returns a formatter that can be use by the current thread if needed to
     * convert Date objects to the Internal representation.
     *
     * @return The {@link java.text.DateFormat} for the current thread
     */
    public static DateFormat getThreadLocalDateFormat() {
        return fmtThreadLocal.get();
    }

    public static TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static ThreadLocalDateFormat fmtThreadLocal = new ThreadLocalDateFormat();

    private static class ThreadLocalDateFormat extends ThreadLocal<DateFormat> {
        DateFormat proto;

        public ThreadLocalDateFormat() {
            super();
            //2007-04-26T08:05:04Z
            SimpleDateFormat tmp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
            tmp.setTimeZone(UTC);
            proto = tmp;
        }

        @Override
        protected DateFormat initialValue() {
            return (DateFormat) proto.clone();
        }
    }

    /** Formats the date and returns the calendar instance that was used (which may be reused) */
    public static Calendar formatDate(Date date, Calendar cal, Appendable out) throws IOException {
        // using a stringBuilder for numbers can be nice since
        // a temporary string isn't used (it's added directly to the
        // builder's buffer.

        StringBuilder sb = out instanceof StringBuilder ? (StringBuilder)out : new StringBuilder();
        if (cal==null) cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ROOT);
        cal.setTime(date);

        int i = cal.get(Calendar.YEAR);
        sb.append(i);
        sb.append('-');
        i = cal.get(Calendar.MONTH) + 1;  // 0 based, so add 1
        if (i<10) sb.append('0');
        sb.append(i);
        sb.append('-');
        i=cal.get(Calendar.DAY_OF_MONTH);
        if (i<10) sb.append('0');
        sb.append(i);
        sb.append('T');
        i=cal.get(Calendar.HOUR_OF_DAY); // 24 hour time format
        if (i<10) sb.append('0');
        sb.append(i);
        sb.append(':');
        i=cal.get(Calendar.MINUTE);
        if (i<10) sb.append('0');
        sb.append(i);
        sb.append(':');
        i=cal.get(Calendar.SECOND);
        if (i<10) sb.append('0');
        sb.append(i);
        i=cal.get(Calendar.MILLISECOND);
        if (i != 0) {
            sb.append('.');
            if (i<100) sb.append('0');
            if (i<10) sb.append('0');
            sb.append(i);

            // handle canonical format specifying fractional
            // seconds shall not end in '0'.  Given the slowness of
            // integer div/mod, simply checking the last character
            // is probably the fastest way to check.
            int lastIdx = sb.length()-1;
            if (sb.charAt(lastIdx)=='0') {
                lastIdx--;
                if (sb.charAt(lastIdx)=='0') {
                    lastIdx--;
                }
                sb.setLength(lastIdx+1);
            }

        }
        sb.append('Z');

        if (out != sb)
            out.append(sb);

        return cal;
    }

    public static final Logger LOG = LoggerFactory
            .getLogger("org.apache.nutch.parse.company");

    private static List<NameValuePair> months = initialize_months();
    private static List<NameValuePair> initialize_months() {
        List<NameValuePair> months = new ArrayList<NameValuePair>();

        try {
            months.add(new NameValuePair(new String("一月".getBytes(), "UTF-8"), "Jan"));
            months.add(new NameValuePair(new String("一月".getBytes(), "UTF-8"), "Jan"));
            months.add(new NameValuePair(new String("二月".getBytes(), "UTF-8"), "Feb"));
            months.add(new NameValuePair(new String("三月".getBytes(), "UTF-8"), "Mar"));
            months.add(new NameValuePair(new String("四月".getBytes(), "UTF-8"), "Apr"));
            months.add(new NameValuePair(new String("五月".getBytes(), "UTF-8"), "May"));
            months.add(new NameValuePair(new String("六月".getBytes(), "UTF-8"), "Jun"));
            months.add(new NameValuePair(new String("七月".getBytes(), "UTF-8"), "Jul"));
            months.add(new NameValuePair(new String("八月".getBytes(), "UTF-8"), "Aug"));
            months.add(new NameValuePair(new String("九月".getBytes(), "UTF-8"), "Sep"));
            months.add(new NameValuePair(new String("十月".getBytes(), "UTF-8"), "Oct"));
            months.add(new NameValuePair(new String("十一月".getBytes(), "UTF-8"), "Nov"));
            months.add(new NameValuePair(new String("十二月".getBytes(), "UTF-8"), "Dec"));
        } catch (Exception e) {
        }
        return months;
    }

    private static String formatDate(String str) {
        str = str.trim();
        /* Microsoft use some chinese word */
        for ( int i = 0; i < months.size(); i++ ) {
            str = str.replaceAll(months.get(i).getName(), months.get(i).getValue());
        }

        try {
            Date d = parseDate(str, DEFAULT_DATE_FORMATS);
            str = getThreadLocalDateFormat().format(d);
        } catch (java.text.ParseException pe) {
            try {
                /* Alibaba use GMT ms after 1970.01.01, give it one more chance */
                long ms = Long.parseLong(str);
                Date d = new Date(ms);
                str = getThreadLocalDateFormat().format(d);
            } catch ( java.lang.NumberFormatException npe ) {
                LOG.warn(" invalid date format(need extend our schema): " + str);
                str = getThreadLocalDateFormat().format(new Date());
            }
        }
        return str;
    }

    public static String formatDate(String d, String format) {
        if ( format != null && !format.isEmpty() ){
            try {
                Perl5Util plutil = new Perl5Util();
                if ( format.equals("MM-dd-yyyy") ) {
                    d = plutil.substitute("s/\\D*(\\d{1,})[^-]*-\\D*(\\d{1,})[^-]*-\\D*(\\d{4})/$3-$1-$2/g", d);
                } else if ( format.equals("MMM dd yyyy") ) {
                    d = plutil.substitute("s/([a-zA-Z]{3})\\S*\\s+\\D*(\\d{1,})\\D+(\\d{4})/$2 $1 $3/g", d);
                } else if ( format.equals("MMM-dd-yyyy") ) {
                    d = plutil.substitute("s/\\s*([a-zA-Z]{3})[^-]*-\\D*(\\d{1,})[^-]*-\\D*(\\d{4})/$2-$1-$3/g", d);
                } else if ( format.equals("dd MM yyyy") ) {
                    d = plutil.substitute("s/\\D*(\\d{1,})\\S*\\s\\D*(\\d{1,})\\S*\\s\\D*(\\d{4})/$3-$2-$1/g", d);
                } else if ( format.equals("yyyy-MM-dd") ) {
                    d = plutil.substitute("s/\\D*(\\d{4})[^-]*-\\D*(\\d{1,})[^-]*-\\D*(\\d{1,})/$1-$2-$3/g", d);
                } else if ( format.equals("yyyy MM dd") ) {
                    d = plutil.substitute("s/\\D*(\\d{4})\\S*\\s\\D*(\\d{1,})\\S*\\s\\D*(\\d{1,})/$1-$2-$3/g", d);
                }
            } catch (MalformedPerl5PatternException me ) {
                LOG.warn("datestring " + d + " cant be matched with " + format);
            }
        }
        return formatDate(d);
    }
    public static String getCurrentDate() {
        return getThreadLocalDateFormat().format(new Date());
    }
    public static boolean nDaysAgo(String date, int days) {
        /* Assuming date in "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" format because we generating it with this format */
        if ( date == null ) return false;
        if ( days == Integer.MAX_VALUE ) return false;
        try {
            long prev = getThreadLocalDateFormat().parse(date).getTime();
            if ((System.currentTimeMillis() - prev) > days * 24 * 60 * 60 * 1000L) 
                return true;
            else
                return false;
        } catch ( Exception e ) {
            return false;
        }
    }
    public static void main(String args[]) {
        String    strs[] = {"m06a - a19b - c2014", "April a15b   year2014a", "April-a15b-year2014a", "d19a   m06b  y2014c",
                "y2014a-m6b - d19c - "};
        String formats[] = {"MM-dd-yyyy", "MMM dd yyyy" , "MMM-dd-yyyy", "dd MM yyyy",
                "yyyy-MM-dd" };
        try {
            for ( int i = 0; i < strs.length; i++ ) {
                String str = strs[i];
                str = formatDate(str, formats[i]);
                System.out.println(strs[i] + " ---> " + str);
            }
        } catch ( Exception e ) {
            e.printStackTrace();
        }

        String prev = "2015-05-03T00:00:00.000Z";
        if ( nDaysAgo(prev, 10) ) {
            System.out.println("yes, n days ago");
        } else {
            System.out.println("not n days ago");
        }
    }
}
