#!/bin/bash

DIR=`pwd`
CLASSPATH=$DIR/build/classes:$DIR/conf
for jar in `find $DIR/build/lib -name *.jar`; do
   CLASSPATH=$CLASSPATH:`readlink -f $jar`
done
export CLASSPATH

#java org.top500.schema.Schema Airbus.json
#java org.top500.indexer.Indexer
#java org.top500.utils.LocationUtils
#java org.top500.utils.DateUtils

if [[ $# -ge 1 ]] && [[ $1 = "resume" ]]; then
java org.top500.fetcher.Fetcher  /tmp/fetchstatus.data
else
java org.top500.fetcher.Fetcher conf/seed.txt regular
#java org.top500.fetcher.Fetcher fetchstatus-2015-09-08T15:08:04.590Z.data resume
#java org.top500.fetcher.Fetcher fetchstatus-2015-09-08T15:08:04.590Z.data regular
fi


