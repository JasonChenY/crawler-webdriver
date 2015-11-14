package org.top500.schema;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.lang.Boolean;
import java.lang.Integer;
import java.lang.StringBuffer;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

public class Schema {
    private String name;
    public Actions actions;
    public Procedure procedure;
    public JobUniqueIdCalc job_unique_id_calc;

    // fields for resuming fetcher
    public boolean fetch_result = true;
    public int fetch_total_jobs = 0;

    // local configurations take high priority than global configurations
    public Boolean use_proxy_specified = false;
    public Boolean use_proxy = false;
    public int fetch_n_pages = -1;
    public int fetch_n_days = -1;
    public int fetch_n_jobs = -1;

    public static void main(String[] args) {
        try {
            System.out.println("new schema from " + args[0]);
            Schema a = new Schema(args[0]);
            a.print();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    public void print() {
        actions.print("");
        procedure.print("");
        if ( job_unique_id_calc!= null ) {
            job_unique_id_calc.print("");
        }
    }
    private void init(Reader input) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(input);
        name = (String) obj.get("name");
        actions = new Actions(obj.get("actions"));
        procedure = new Procedure(obj.get("procedure"));
        if ( obj.get("job_unique_id_calc") != null ) {
            job_unique_id_calc = new JobUniqueIdCalc(obj.get("job_unique_id_calc"));
        } else {
            job_unique_id_calc = null;
        }

        if ( obj.get("use_proxy") != null ) {
            use_proxy = (Boolean)obj.get("use_proxy");
            use_proxy_specified = true;
        }
        if ( obj.get("fetch.n.pages") != null ) {
            fetch_n_pages = Integer.valueOf(((Long) obj.get("fetch.n.pages")).intValue());
        }
        if ( obj.get("fetch.n.days") != null ) {
            fetch_n_days = Integer.valueOf(((Long) obj.get("fetch.n.days")).intValue());
        }
        if ( obj.get("fetch.n.jobs") != null ) {
            fetch_n_jobs = Integer.valueOf(((Long) obj.get("fetch.n.jobs")).intValue());
        }
    }
    public String getName() { return name; }

    public Schema(Reader input) throws Exception {
        init(input);
    }
    public Schema(String filename) throws Exception {
        //FileReader fr = new FileReader(filename);
        //fr.close();
        InputStream input = Schema.class.getClassLoader().getResourceAsStream(filename);
        BufferedReader bf = new BufferedReader(new InputStreamReader(input));
        init(bf);
    }

    public void setFetchRuntimeIndex(String idxstr) {
        StringTokenizer tokenizer = new StringTokenizer(idxstr, ".");
        Procedure proc = procedure;
        while (tokenizer.hasMoreTokens() && (proc!=null) ) {
            String token = tokenizer.nextToken();
            try {
                int index = Integer.parseInt(token);
                proc.fetch_runtime_index = index;
            } catch ( Exception e ) {
                return;
            }
            proc = proc.procedure;
        }
    }

    public String getFetchRuntimeIndex() {
        StringBuffer buffer = new StringBuffer();
        Procedure proc = procedure;
        while ( proc != null ) {
            buffer.append(proc.fetch_runtime_index);
            proc = proc.procedure;
            if ( proc != null ) buffer.append(".");
        }
        return buffer.toString();
    }

    public class JobUniqueIdCalc {
        public String how = null;
        public String value = null;
        public JobUniqueIdCalc(Object o) throws Exception {
            if ( o == null ) return;
            JSONObject obj = (JSONObject)o;
            how = (String) obj.get("how");
            value = (String) obj.get("value");
        }
        public void print(String ident) {
            System.out.println(ident + "'job_unique_id_calc':{'how':'" + how + "','value':'" + value + "'}");
        }
    }

    public class Transform {
        public String how;
        public String value;
        public int which;
        public int group;
        public Transform(Object o) {
            if ( o == null ) return;
            //JSONObject obj = (JSONObject)o;
            Map<String, Object> obj = (Map<String,Object>)o;
            how = (String) obj.get("how");
            value = (String) obj.get("value");

            if ( how.equals("regex_matcher") ) {
                if (obj.get("which") == null)
                    which = 0;
                else
                    which = Integer.valueOf(((Long) obj.get("which")).intValue());

                if (obj.get("group") == null)
                    group = 1;
                else
                    group = Integer.valueOf(((Long) obj.get("group")).intValue());
            }
        }
        public void print(String ident) {
            System.out.println(ident + "{'how':" + how + "','value':'" + value + "'}");
        }
    }

    public class Element {
        public String element;
        public String how;
        public String method;
        public String value; /* optional, to specify which attr to extract with method 'getAttribute' */
        public List<Transform> transforms;
        public Element(Object o) throws Exception {
            if ( o == null ) return;
            Map<String, Object> map = (Map<String,Object>)o;

            Iterator iter = map.entrySet().iterator();
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                String name = (String)entry.getKey();
                if ( name.equals("transforms")) {
                    transforms = new ArrayList<Transform>();
                    //JSONArray array = (JSONArray)entry.getValue();
                    List<Object> array = (List<Object>)entry.getValue();

                    for ( int i = 0; i < array.size(); i++ ) {
                        Transform transform = new Transform(array.get(i));
                        transforms.add(transform);
                    }
                } else {
                    String val = (String) entry.getValue();
                    if (name.equals("element")) element = val;
                    if (name.equals("how")) how = val;
                    if (name.equals("method")) method = val;
                    if (name.equals("value")) value = val;
                }
            }
        }
        public Element(String name, String h) {
            element = name;
            how = h;
        }
        public void print(String ident) {
            System.out.println(ident + "{");
            System.out.println(ident + "    'element':'" + element + "',");
            System.out.println(ident + "    'how':'" + how + "',");
            if ( method != null ) {
                System.out.println(ident + "    'method':'" + method + "',");
            }
            if ( value != null ) {
                System.out.println(ident + "    'value':'" + value + "',");
            }
            if ( transforms != null ) {
                System.out.println(ident + "'transforms' : [");
                for ( int i = 0; i < transforms.size(); i++ ) transforms.get(i).print(ident + "   ");
                System.out.println(ident + "],");
            }
            System.out.println(ident + "}");
        }
    }
    public class Extracts  {
        public Map<String, Element> items = new HashMap<String, Element>();
        public Extracts(Object obj) throws Exception {
            if ( obj == null ) return;
            ContainerFactory containerFactory = new ContainerFactory(){
                public List creatArrayContainer() {
                    return new LinkedList();
                }
                public Map createObjectContainer() {
                    return new LinkedHashMap();
                }
            };

            Map<String, Object> output = (Map)(new JSONParser()).parse(((JSONObject)obj).toString(), containerFactory);

            Iterator iter = output.entrySet().iterator();
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                items.put((String)entry.getKey(), new Element(entry.getValue()));
            }
        }
        public void print(String ident) {
            System.out.println(ident + "'extracts':{");
            Iterator iter = items.entrySet().iterator();
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                System.out.println(ident + "    '" + entry.getKey() + "':");
                ((Element)entry.getValue()).print(ident + "        ");
            }
            System.out.println(ident + "}");
        }
    }

    public class Expection {
        public String condition;
        public Element element;
        public String value; /* optional */
        public Expection(Object o) throws Exception {
            if ( o == null ) return;

            ContainerFactory containerFactory = new ContainerFactory(){
                public List creatArrayContainer() {
                    return new LinkedList();
                }
                public Map createObjectContainer() {
                    return new LinkedHashMap();
                }
            };

            Map<String, Object> output = (Map)(new JSONParser()).parse(((JSONObject)o).toString(), containerFactory);

            Iterator iter = output.entrySet().iterator();
            String ele_name = null;
            String ele_how = null;
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                String name = (String)entry.getKey();
                String val = (String)entry.getValue();
                switch ( name ) {
                    case "condition": condition = val; break;
                    case "element": ele_name = val; break;
                    case "how": ele_how = val; break;
                    case "value" : value = val; break;
                    default: break;
                }
            }
            if ( ele_name != null ) element = new Element(ele_name, ele_how);
        }
        public void print(String ident) {
            System.out.println(ident+"{'condition':'" + condition + "',");
            if (element != null ) element.print(ident);
            System.out.println(ident + ",'value':'" + value + "'},");
        }
    }

    public class Expections {
        public List<Expection> expections = new ArrayList<Expection>();
        public Expections(Object o) throws Exception {
            if ( o == null ) return;
            JSONArray array = (JSONArray)o;
            for ( int i = 0; i < array.size(); i++ ) {
                Expection expection = new Expection(array.get(i));
                expections.add(expection);
            }
        }
        public void print(String ident) {
            System.out.println(ident+"'expections': [");
            for ( int i = 0; i < expections.size(); i++ ) {
                expections.get(i).print(ident+"    ");
            }
            System.out.println(ident+"],");
        }

    }

    public enum CmdType {None, Load, Set, Click, Submit, Back, Forward, Refresh, Restore,
                         ScrollIntoView, selectByVisibleText, selectByValue, zoom,
                         openInNewTab, sendKeys, switchToMainFrame, setPage, openInNewTab_ContextClick, executeScript, moveToElement};
    public class Command {
        /* 'click':  if not configured ( default value ) ----> changed to None if not configured
           'None':   if unknown cmd configured.
         */
        public CmdType code;
        public Command(Object o) {
            if ( o == null ) {
                code = CmdType.None;
            } else {
                String cmd = (String)o;
                if ( cmd == null )
                    code = CmdType.None;
                else {
                    switch (cmd) {
                        case "load":
                            code = CmdType.Load;
                            break;
                        case "set":
                            code = CmdType.Set;
                            break;
                        case "click":
                            code = CmdType.Click;
                            break;
                        case "submit":
                            code = CmdType.Submit;
                            break;
                        case "back":
                            code = CmdType.Back;
                            break;
                        case "forward":
                            code = CmdType.Forward;
                            break;
                        case "refresh":
                            code = CmdType.Refresh;
                            break;
                        case "restore":
                            code = CmdType.Restore;
                            break;
                        case "ScrollIntoView":
                            code = CmdType.ScrollIntoView;
                            break;
                        case "selectByVisibleText":
                            code = CmdType.selectByVisibleText;
                            break;
                        case "selectByValue":
                            code = CmdType.selectByValue;
                            break;
                        case "zoom":
                            code = CmdType.zoom;
                            break;
                        case "openInNewTab":
                            code = CmdType.openInNewTab;
                            //CTRL + click
                            break;
                        case "sendKeys":
                            code = CmdType.sendKeys;
                            break;
                        case "switchToMainFrame":
                            code = CmdType.switchToMainFrame;
                            break;
                        case "setPage":
                            // in fact using sendKeys, just with value by runtime data.
                            code = CmdType.setPage;
                            break;
                        case "openInNewTab_ContextClick":
                            code = CmdType.openInNewTab_ContextClick;
                            //Right click
                            break;
                        case "executeScript":
                            code = CmdType.executeScript;
                            break;
                        case "moveToElement":
                            code = CmdType.moveToElement;
                            break;
                        default:
                            code = CmdType.None;
                            break;
                    }
                }
            }
        }
        public void print(String ident) {
            System.out.println(ident + "'cmd':'" + code + "'");
        }
    }

    public class Action {
        public Action preaction = null; //for Baidu, where jobs categorized via location, should first focus on the location button.
        public Element element = null;
        public Command command;
        public String setvalue;
        public Expections expections = null;
        public boolean isFatal = true;
        public boolean debug = false;

        public Action(Object o) throws Exception {
            if ( o == null ) return;
            JSONObject obj = (JSONObject)o;

            if ( obj.get("preaction") != null ) {
                preaction = new Action(obj.get("preaction"));
            }
            String name = (String) obj.get("element");
            String how = (String) obj.get("how");
            if ( name != null ) element = new Element(name, how);

            command = new Command(obj.get("cmd"));
            setvalue = (String)obj.get("value");

            if ( obj.get("expections") != null) {
                expections = new Expections(obj.get("expections"));
            }

            if ( obj.get("debug") != null ) {
                debug = (Boolean)obj.get("debug");
            }

            if ( obj.get("isFatal") != null ) {
                isFatal = (Boolean)obj.get("isFatal");
            }
        }
        public void print(String ident) {
            System.out.println(ident+"{");
            if ( element != null ) element.print(ident+"    ");
            command.print(ident+"    ");
            System.out.println(ident + "    'value':'"+setvalue+"'");
            if ( expections != null )
                expections.print(ident+"    ");
            else
                System.out.println(ident + "     no expection");
            System.out.println(ident + "    'debug:'" + debug + "'");
            System.out.println(ident+"},");
        }
    }

    public class Actions {
        public List<Action> actions = new ArrayList<Action>();
        public Actions(Object o) throws Exception {
            if ( o == null ) return;
            JSONArray array = (JSONArray)o;
            for ( int i = 0; i < array.size(); i++ ) {
                Action action = new Action(array.get(i));
                actions.add(action);
            }
        }
        public void print(String ident) {
            System.out.println(ident+"'actions': [");
            for ( int i = 0; i < actions.size(); i++ ) {
                actions.get(i).print(ident+"    ");
            }
            System.out.println(ident+"],");
        }
    }
    public enum LOOP_TYPE {NONE, BEGIN, END}
    public enum LOOP_ITEM_TYPE {JOB,PAGE,OTHER}
    public class Procedure {
        public String xpath_prefix_loop = "";
        public LOOP_TYPE loop_type = LOOP_TYPE.NONE;
        public int begin_from = 0;
        public int end_to = 0;
        public LOOP_ITEM_TYPE loop_item_type = LOOP_ITEM_TYPE.JOB; // for Vanke, we will break for PAGE loop if return PROC_RESULT_OK_NDAYS, but continue for OTHER.
        public boolean loop_need_initial_action = false;
        public Element loop_totalpages = null;
        public Extracts extracts = null;
        public Actions actions = null;
        public Procedure procedure = null;
        public int fetch_runtime_index = -1;

        public Procedure(Object o) throws Exception {
            if ( o == null ) return;
            JSONObject obj = (JSONObject)o;

            if ( obj.get("loop") != null ) {
                JSONObject loop = (JSONObject)obj.get("loop");

                if (loop.get("xpath_prefix") != null) {
                    xpath_prefix_loop = (String) loop.get("xpath_prefix");
                }
                if (loop.get("loop_type") != null) {
                    String type = (String) loop.get("loop_type");
                    if (type.equals("begin")) {
                        loop_type = LOOP_TYPE.BEGIN;
                        if ( loop.get("begin_from") != null ) {
                            begin_from = Integer.valueOf(((Long) loop.get("begin_from")).intValue());
                        }
                        if ( loop.get("end_to") != null ) {
                            end_to = Integer.valueOf(((Long) loop.get("end_to")).intValue());
                        }
                        if ( loop.get("loop_item_type") != null ) {
                            String item_type = (String)loop.get("loop_item_type");
                            switch (item_type) {
                                case "job": loop_item_type = LOOP_ITEM_TYPE.JOB; break;
                                case "page": loop_item_type = LOOP_ITEM_TYPE.PAGE; break;
                                case "other": loop_item_type = LOOP_ITEM_TYPE.OTHER; break;
                                default: throw new Exception("unknow loop_item_type" + item_type);
                            }
                            if ( loop.get("loop_totalpages") != null ) {
                                loop_totalpages = new Element(loop.get("loop_totalpages"));
                            }
                            if ( loop.get("loop_need_initial_action") != null ) {
                                loop_need_initial_action = (Boolean)loop.get("loop_need_initial_action");
                            }
                        }
                    } else if (type.equals("end")) {
                        loop_type = LOOP_TYPE.END;
                    }
                }
                if ( loop.get("actions") != null ) {
                    actions = new Actions(loop.get("actions"));
                }
            }

            if ( obj.get("actions") != null ) {
                actions = new Actions(obj.get("actions"));
            }
            if ( obj.get("extracts") != null ) {
                extracts = new Extracts(obj.get("extracts"));
            }
            if ( obj.get("procedure") != null ) {
                procedure = new Procedure(obj.get("procedure"));
            }
        }
        public void print(String ident) {
            if ( extracts == null && actions == null && procedure == null ) return;
            System.out.println(ident + "'Procedure':{");
            System.out.println(ident + "  'xpath_prefix_loop':'" + xpath_prefix_loop + "'");
            System.out.println(ident + "  'loop_type':'" + loop_type + "'");
            System.out.println(ident + "  'begin_from':'" + begin_from + "'");
            System.out.println(ident + "  'end_to':'" + end_to + "'");
            System.out.println(ident + "  'loop_item_type':'" + loop_item_type + "'");
            if ( loop_totalpages != null ) {
                System.out.print(ident + "  'loop_totalpages':");
                loop_totalpages.print(ident + "  ");
            }
            if ( extracts != null ) extracts.print(ident+"  ");
            if ( actions != null ) actions.print(ident+"  ");
            if ( procedure != null ) procedure.print(ident+"  ");
            System.out.println(ident + "}");
        }
    }
}

