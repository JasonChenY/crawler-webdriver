#!/bin/bash

DIR=`pwd`
CLASSPATH=$DIR/build/classes:$DIR/conf
for jar in `find $DIR/build/lib -name *.jar`; do
   if [[ "$jar" =~ solr-solrj.*jar ]] || [[ "$jar" =~ zookeeper.*jar ]] || [[ "$jar" =~ noggit.*jar ]];then
       continue
   fi
   CLASSPATH=$CLASSPATH:`readlink -f $jar`
done

if [[ $# -ge 1 ]] && [[ $1 = "5.1" ]]; then
    CLASSPATH=$CLASSPATH:/sdk/tmp/ivy/cache/org.apache.solr/solr-solrj/jars/solr-solrj-5.1.0.jar:/sdk/tmp/ivy/cache/org.apache.zookeeper/zookeeper/jars/zookeeper-3.4.6.jar:/sdk/tmp/ivy/cache/org.noggit/noggit/jars/noggit-0.6.jar
else
    CLASSPATH=$CLASSPATH:/sdk/tmp/ivy/cache/org.apache.solr/solr-solrj/jars/solr-solrj-4.6.0.jar:/sdk/tmp/ivy/cache/org.apache.zookeeper/zookeeper/jars/zookeeper-3.4.5.jar:/sdk/tmp/ivy/cache/org.noggit/noggit/jars/noggit-0.5.jar
fi    
export CLASSPATH

#java org.top500.schema.Schema Airbus.json

#java org.top500.indexer.Indexer

if [[ $# -ge 2 ]] && [[ $2 = "resume" ]]; then
java org.top500.fetcher.Fetcher  /tmp/fetchstatus.data
else
java org.top500.fetcher.Fetcher conf/seed.txt
fi


