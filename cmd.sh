#!/bin/sh
DIR=`pwd`
CLASSPATH=$DIR/build/classes:$DIR/conf
for i in `find build/lib -name *.jar`; do
   CLASSPATH=$CLASSPATH:`readlink -f $i`
done
export CLASSPATH
echo $CLASSPATH

java org.top500.schema.Schema schema.template
java org.top500.fetcher.Fetcher

