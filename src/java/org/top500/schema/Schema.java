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
    public String name;
    public Actions actions;
    public Extracts extracts;
    public Procedure procedure;
    public Restore restore;

    public static void main(String[] args) {
        try {
            Schema a = new Schema("schema.template");
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    private void init(Reader input) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject obj = (JSONObject) parser.parse(input);
        name = (String) obj.get("name");
        actions = new Actions(obj.get("actions"));
        extracts = new Extracts(obj.get("extracts"));
        procedure = new Procedure(obj.get("procedure"));
        restore = new Restore(obj.get("restore"));
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
                System.out.println("      " + name + " : " + value);
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
            System.out.println("Extracts: ");
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                System.out.println(entry.getKey() + " : ");
                items.put((String)entry.getKey(), new Element(entry.getValue()));
            }
        }
        public void print(String ident) {
            System.out.println(ident + "{");
            Iterator iter = items.entrySet().iterator();
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                System.out.println(ident + "    '" + entry.getKey() + "':");
                ((Element)entry.getValue()).print(ident + "    ");
            }
        }
    }

    enum RestoreType {None, CloseWindow, NavigateBack;}
    public class Restore {
        public RestoreType code;
        public Restore(Object o) {
            if ( o == null ) {
                code = RestoreType.None;
            } else {
                System.out.println((String) o);
                switch ((String) o) {
                    case "close":
                        code = RestoreType.CloseWindow;
                        break;
                    case "back":
                        code = RestoreType.NavigateBack;
                        break;
                    default:
                        code = RestoreType.None;
                        break;
                }
            }
        }
    }

    public class Procedure {
        public String loop_elements;
        public int index; /* used for indicating which item has been processed */
        public boolean skipFirst = false;
        public Extracts extracts;
        public Command command;
        public Expected expected;
        public Procedure procedure;
        public Restore restore;

        public Procedure(Object o) throws Exception {
            if ( o == null ) return;
            JSONObject obj = (JSONObject)o;
            loop_elements = (String) obj.get("loop_elements");
            System.out.println(loop_elements);
            index = 0;
            if ( obj.get("skipfirst") != null) {
                skipFirst = (Boolean) obj.get("skipfirst");
            }
            System.out.println(skipFirst);
            extracts = new Extracts(obj.get("extracts"));
            command = new Command(obj.get("command"));
            expected = new Expected(obj.get("expected"));
            procedure = new Procedure(obj.get("procedure"));
            restore = new Restore(obj.get("restore"));
        }
    }

    public class Expected {
        public String condition;
        public Element element;
        public String value; /* optional */
        public Expected(Object o) throws Exception {
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
            System.out.println("Expected: ");
            String ele_name = null;
            String ele_how = null;
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                String name = (String)entry.getKey();
                String val = (String)entry.getValue();
                System.out.println("      " + name + " : " + val);
                switch ( name ) {
                    case "condition": condition = val; break;
                    case "element": ele_name = val; break;
                    case "how": ele_how = val; break;
                    case "value" : value = val; break;
                    default: break;
                }
            }
            if ( ele_name != null & ele_how != null ) element = new Element(ele_name, ele_how);
        }
    }
    public class Action {
        public Element element;
        public Command command;
        public String setvalue;
        public Expected expected;

        public Action(Object o) throws Exception {
            if ( o == null ) return;
            JSONObject obj = (JSONObject)o;

            String name = (String) obj.get("element");
            String how = (String) obj.get("how");
            element = new Element(name, how);

            command = new Command(o);
            setvalue = (String)obj.get("value");
            expected = new Expected(obj.get("expected"));

            System.out.println(name + " " + how);
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
    }

    enum CmdType {None, Load, Set, Click, Submit};
    public class Command {
        public CmdType code;
        public Command(Object o) {
            if ( o == null ) {
                code = CmdType.None;
            } else {
                String cmd = (String)((JSONObject) o).get("command");
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
                        default:
                            code = CmdType.None;
                            break;
                    }
                }
            }
        }
    }
}

