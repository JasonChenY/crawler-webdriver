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
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.Iterator;

public class Schema {
    private String name;
    public Actions actions;
    public Procedure procedure;
    private String job_date_format;
    private String job_location_format_regex;

    public static void main(String[] args) {
        try {
            Schema a = new Schema("schema.template");

            a.print();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    public void print() {
        actions.print("");
        procedure.print("");
    }
    private void init(Reader input) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(input);
        name = (String) obj.get("name");
        actions = new Actions(obj.get("actions"));
        procedure = new Procedure(obj.get("procedure"));
        job_date_format = (String)obj.get("job_date_format");
        job_location_format_regex = (String)obj.get("job_location_format_regex");
    }
    public String getName() { return name; }
    public String getJob_date_format() {
        return job_date_format;
    }
    public String getJob_location_format_regex() {
        return job_location_format_regex;
    }

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
    public class Element {
        public String element;
        public String how;
        public Element(Object o) throws Exception {
            if ( o == null ) return;
            Map<String, String> map = (Map<String,String>)o;

            Iterator iter = map.entrySet().iterator();
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                String name = (String)entry.getKey();
                String value = (String)entry.getValue();
                if ( name.equals("element") ) element = value;
                if ( name.equals("how") ) how = value;
            }
        }
        public Element(String name, String h) {
            element = name;
            how = h;
        }
        public void print(String ident) {
            System.out.println(ident + "{" + "element:'" + element + "',how:'" + how + "'}");
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
                System.out.print(ident + "    '" + entry.getKey() + "':");
                ((Element)entry.getValue()).print("");
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
            System.out.println(ident+"'Expection':{'condition':'" + condition + "',");
            if (element != null ) element.print(ident + "            ");
            System.out.println(ident + "            ,'value':" + value + "',");
        }
    }


    public enum CmdType {None, Load, Set, Click, Submit, Back, Forward, Refresh, Restore};
    public class Command {
        /* 'click':  if not configured ( default value )
           'None':   if unknown cmd configured.
         */
        public CmdType code;
        public Command(Object o) {
            if ( o == null ) {
                code = CmdType.Click;
            } else {
                String cmd = (String)o;
                if ( cmd == null )
                    code = CmdType.Click;
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
        public Element element;
        public Command command;
        public String setvalue;
        public Expection expection = null;
        public boolean debug = false;

        public Action(Object o) throws Exception {
            if ( o == null ) return;
            JSONObject obj = (JSONObject)o;

            String name = (String) obj.get("element");
            String how = (String) obj.get("how");
            element = new Element(name, how);

            command = new Command(obj.get("cmd"));
            setvalue = (String)obj.get("value");

            if ( obj.get("expection") != null) {
                expection = new Expection(obj.get("expection"));
            }

            if ( obj.get("debug") != null ) {
                debug = (Boolean)obj.get("debug");
            }
        }
        public void print(String ident) {
            System.out.println(ident+"{");
            element.print(ident+"    ");
            command.print(ident+"    ");
            System.out.println(ident + "    'value':'"+setvalue+"'");
            if ( expection != null )
                expection.print(ident+"    ");
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
    public class Procedure {
        public String xpath_prefix_loop = "";
        public LOOP_TYPE loop_type = LOOP_TYPE.NONE;
        public Extracts extracts = null;
        public Actions actions = null;
        public Procedure procedure = null;

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
                    } else if (type.equals("end")) {
                        loop_type = LOOP_TYPE.END;
                    }
                }
                actions = new Actions(loop.get("actions"));
            }

            if ( obj.get("actions") != null ) {
                actions = new Actions(obj.get("actions"));
            }

            extracts = new Extracts(obj.get("extracts"));
            procedure = new Procedure(obj.get("procedure"));
        }
        public void print(String ident) {
            if ( extracts == null && actions == null && procedure == null ) return;
            System.out.println(ident + "'Procedure':{");
            System.out.println(ident + "  'xpath_prefix_loop':'" + xpath_prefix_loop + "'");
            System.out.println(ident + "  'loop_type':'" + loop_type + "'");
            if ( extracts != null ) extracts.print(ident+"  ");
            if ( actions != null ) actions.print(ident+"  ");
            if ( procedure != null ) procedure.print(ident+"  ");
            System.out.println(ident + "}");
        }
    }
}

