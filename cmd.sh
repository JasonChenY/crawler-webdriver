#!/bin/bash

#crontab -l
#18 0 * * * cd /opt/jobs/crawler; ./cmd.sh routine
#30 13,21,23 * * * cd /opt/crazyfang; python fetch.py

DIR=`pwd`
CLASSPATH=$DIR/conf
for jar in `find $DIR/lib -name "*.jar"`; do
   CLASSPATH=$CLASSPATH:`readlink -f $jar`
done
export CLASSPATH
export PATH=$PATH:/opt/jdk1.7.0_75/bin

#java org.top500.schema.Schema Airbus.json
#java org.top500.indexer.Indexer
#java org.top500.utils.LocationUtils
#java org.top500.utils.DateUtils
usage() {
echo "$0                   ----to fetch
         resume            ----to resume from point stopped last time
         verify            ----to verify whether job still valid
         verify name       ----to verify jobs for single company
         routine           ----daily work";
}

zombie_pid=$(pgrep -f "java -jar verifier-2.3.jar")
if [ -n "$zombie_pid" ]; then
   echo "kill hang verifier last day"
   kill -9 $zombie_pid 
fi

if [ $# -ge 1 ]; then
   if [ $1 = "resume" ]; then
      java -jar crawler-2.3.jar /tmp/fetchstatus.data
   elif [ $1 = "verify" ]; then
      if [[ $# -ge 2 ]]; then
         java -jar verifier-2.3.jar  $2
      else
         java -jar verifier-2.3.jar
      fi 
   elif [ $1 = "routine" ]; then
      sed -i 's/verify.log/fetcher.log/g' $DIR/conf/log4j.properties
      java -jar crawler-2.3.jar conf/seed.txt regular
      sleep 60
      sed -i 's/fetcher.log/verify.log/g' $DIR/conf/log4j.properties
      java -jar verifier-2.3.jar
   else
      usage
   fi
else
   java -jar crawler-2.3.jar conf/seed.txt regular
fi
