#!/bin/sh
DIR=`pwd`
CLASSPATH=$DIR/build/classes:$DIR/conf
for i in `find build/lib -name *.jar`; do
   CLASSPATH=$CLASSPATH:`readlink -f $i`
done
export CLASSPATH

#for i in $(seq 2 2); do cp conf/schema.template conf/schema$i;done
#java org.top500.schema.Schema schema.template
#java org.top500.indexer.Indexer

rm webdriver.log
java org.top500.fetcher.Fetcher conf/seed.txt

