package org.top500.indexer;

//import org.apache.http.impl.client.DefaultHttpClient;
//import org.apache.http.auth.AuthScope;
//import org.apache.http.client.params.HttpClientParams;
//import org.apache.http.params.HttpParams;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.auth.AuthScope;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.top500.utils.Configuration;

import java.net.MalformedURLException;

public class SolrUtils {

  public static Logger LOG = LoggerFactory.getLogger(SolrUtils.class);

  public static HttpSolrServer getHttpSolrServer(Configuration conf) throws MalformedURLException {
      HttpClientBuilder httpClientBuilder = HttpClients.custom();

      if (conf.getBoolean(SolrConstants.USE_AUTH, false)) {
          String username = conf.get(SolrConstants.USERNAME);
          String password = conf.get(SolrConstants.PASSWORD);
          LOG.info("Authenticating as: " + username);

          UsernamePasswordCredentials cred = new UsernamePasswordCredentials(username, password);
          AuthScope authScope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME);
          CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
          credentialsProvider.setCredentials(authScope, cred);

          httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
      }

      CloseableHttpClient client = httpClientBuilder.build();

      return new HttpSolrServer(conf.get(SolrConstants.SERVER_URL), client);
  }
}