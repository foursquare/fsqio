#!/bin/bash

if [ "$1" = "" ]; then
  echo "usage: $0 <2-letter-country-code>"
  echo "ex: $0 US"
  echo "ex: $0 JP"
  exit 1
fi

unzip -v >/dev/null 2>&1 || { echo >&2 "I require 'unzip' but it's not installed.  Aborting."; exit 1; }
curl -h  >/dev/null 2>&1 || { echo >&2 "I require 'curl' but it's not installed.  Aborting."; exit 1; }

COUNTRY=$1

set -x

mkdir -p src/jvm/io/fsq/twofishes/indexer/data/downloaded/
mkdir -p src/jvm/io/fsq/twofishes/indexer/data/downloaded/zip/

FILE=src/jvm/io/fsq/twofishes/indexer/data/downloaded/$COUNTRY.txt
if [ -f $FILE ];
then
   echo "File $FILE exists."
else
   curl -o $FILE.zip http://download.geonames.org/export/dump/$COUNTRY.zip
   unzip $FILE.zip
   mv $COUNTRY.txt $FILE
   rm readme.txt
   rm $FILE.zip
fi


FILE=src/jvm/io/fsq/twofishes/indexer/data/downloaded/zip/$COUNTRY.txt
if [ -f $FILE ];
then
   echo "File $FILE exists."
else
   curl -o $FILE.zip http://download.geonames.org/export/zip/$COUNTRY.zip
   unzip $FILE.zip
   mv $COUNTRY.txt $FILE
   rm readme.txt
   rm $FILE.zip
fi

source src/jvm/io/fsq/twofishes/scripts/download-common.sh
./src/jvm/io/fsq/twofishes/scripts/extract-wiki-buildings.py $COUNTRY
./src/jvm/io/fsq/twofishes/scripts/extract-adm.py $COUNTRY
