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
        JSONParser parser = new JSONParser();;
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

    public class Extracts  {
        public Map<String, String> items = new HashMap<String, String>();
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
            items = (Map)(new JSONParser()).parse(((JSONObject)obj).toString(), containerFactory);
            Iterator iter = items.entrySet().iterator();
            System.out.println("Extracts: ");
            while(iter.hasNext()){
                Map.Entry entry = (Map.Entry)iter.next();
                System.out.println(entry.getKey() + "=>" + entry.getValue());
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
        public Waitfor wait;
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
            wait = new Waitfor(obj.get("wait"));
            procedure = new Procedure(obj.get("procedure"));
            restore = new Restore(obj.get("restore"));
        }
    }

    public class Waitfor {
        public String wait_str;
        public Waitfor(Object o) {
            wait_str  = (String)o;
        }
    }
    public class Action {
        public String element;
        public Command command;
        public String setvalue;
        public Waitfor wait;

        public Action(Object o) throws Exception {
            JSONObject obj = (JSONObject)o;
            element = (String) obj.get("element");
            command = new Command(o);
            setvalue = (String)obj.get("value");
            wait = new Waitfor(obj.get("wait"));
            System.out.println(element);
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

