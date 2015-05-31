package org.top500.indexer;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.top500.utils.Configuration;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class SolrMappingReader {
  public static Logger LOG = LoggerFactory.getLogger(SolrMappingReader.class);

  private Configuration conf;

  private Map<String, String> keyMap = new HashMap<String, String>();
  private Map<String, String> copyMap = new HashMap<String, String>();
  private String uniqueKey = "id";

  private static SolrMappingReader _instance;
  public static synchronized SolrMappingReader getInstance(Configuration conf) {
    if (_instance == null) {
       _instance = new SolrMappingReader(conf);
    }
    return _instance;
  }

  protected SolrMappingReader(Configuration conf) {
    String filename = conf.get(SolrConstants.MAPPING_FILE, "solrindex-mapping.xml");
    InputStream ssInputStream =  SolrMappingReader.class.getClassLoader().getResourceAsStream(filename);
    InputSource inputSource = new InputSource(ssInputStream);
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(inputSource);
      Element rootElement = document.getDocumentElement();
      NodeList fieldList = rootElement.getElementsByTagName("field");
      if (fieldList.getLength() > 0) {
        for (int i = 0; i < fieldList.getLength(); i++) {
          Element element = (Element) fieldList.item(i);
          LOG.info("source: " + element.getAttribute("source") + " dest: "
              + element.getAttribute("dest"));
          keyMap.put(element.getAttribute("source"),
              element.getAttribute("dest"));
        }
      }
      NodeList copyFieldList = rootElement.getElementsByTagName("copyField");
      if (copyFieldList.getLength() > 0) {
        for (int i = 0; i < copyFieldList.getLength(); i++) {
          Element element = (Element) copyFieldList.item(i);
          LOG.info("source: " + element.getAttribute("source") + " dest: "
              + element.getAttribute("dest"));
          copyMap.put(element.getAttribute("source"),
              element.getAttribute("dest"));
        }
      }
      NodeList uniqueKeyItem = rootElement.getElementsByTagName("uniqueKey");
      if (uniqueKeyItem.getLength() > 1) {
        LOG.warn("More than one unique key definitions found in solr index mapping, using default 'id'");
        uniqueKey = "id";
      } else if (uniqueKeyItem.getLength() == 0) {
        LOG.warn("No unique key definition found in solr index mapping using, default 'id'");
      } else {
        uniqueKey = uniqueKeyItem.item(0).getFirstChild().getNodeValue();
      }
    } catch (MalformedURLException e) {
      LOG.warn(e.toString());
    } catch (SAXException e) {
      LOG.warn(e.toString());
    } catch (IOException e) {
      LOG.warn(e.toString());
    } catch (ParserConfigurationException e) {
      LOG.warn(e.toString());
    }
  }

  public Map<String, String> getKeyMap() {
    return keyMap;
  }

  public Map<String, String> getCopyMap() {
    return copyMap;
  }

  public String getUniqueKey() {
    return uniqueKey;
  }

  public String hasCopy(String key) {
    if (copyMap.containsKey(key)) {
      key = copyMap.get(key);
    }
    return key;
  }

  public String mapKey(String key) throws IOException {
    if (keyMap.containsKey(key)) {
      key = keyMap.get(key);
    }
    return key;
  }

  public String mapCopyKey(String key) throws IOException {
    if (copyMap.containsKey(key)) {
      key = copyMap.get(key);
    }
    return key;
  }
}
