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
usage() {
echo "$0                   ----to fetch
         resume            ----to resume from point stopped last time
         verify            ----to verify whether job still valid
         verify name       ----to verify jobs for single company";
}

if [ $# -ge 1 ]; then
   if [ $1 = "resume" ]; then
      java org.top500.fetcher.Fetcher  /tmp/fetchstatus.data
   elif [ $1 = "verify" ]; then
      if [[ $# -ge 2 ]]; then
         java org.top500.verifier.Verifier $2
      else
         java org.top500.verifier.Verifier
      fi 
   else
      usage
   fi
else
   java org.top500.fetcher.Fetcher conf/seed.txt regular
fi

