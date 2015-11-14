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
import java.io.IOException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpException;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;

public class SolrUtils {

  public static Logger LOG = LoggerFactory.getLogger(SolrUtils.class);

  private static class PreemptiveAuthInterceptor implements HttpRequestInterceptor {
      public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
          AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);

          // If no auth scheme avaialble yet, try to initialize it
          // preemptively
          if (authState.getAuthScheme() == null) {
              CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(ClientContext.CREDS_PROVIDER);
              HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
              Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
              if (creds == null)
                  throw new HttpException("No credentials for preemptive authentication");
              authState.setAuthScheme(new BasicScheme());
              authState.setCredentials(creds);
          }
      }
  }

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
          httpClientBuilder.addInterceptorFirst(new PreemptiveAuthInterceptor());
      }

      CloseableHttpClient client = httpClientBuilder.build();

      return new HttpSolrServer(conf.get(SolrConstants.SERVER_URL), client);
  }
}