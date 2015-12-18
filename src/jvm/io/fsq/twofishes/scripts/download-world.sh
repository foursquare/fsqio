#!/bin/bash

set -x

unzip -v >/dev/null 2>&1 || { echo >&2 "I require 'unzip' but it's not installed.  Aborting."; exit 1; }
curl -h >/dev/null 2>&1 || { echo >&2 "I require 'curl' but it's not installed.  Aborting."; exit 1; }

mkdir -p src/jvm/io/fsq/twofishes/indexer/data/downloaded/
mkdir -p src/jvm/io/fsq/twofishes/indexer/data/downloaded/zip/

FILE=src/jvm/io/fsq/twofishes/indexer/data/downloaded/allCountries.txt
if [ -f $FILE ];
then
   echo "File $FILE exists."
else
   curl -o $FILE.zip http://download.geonames.org/export/dump/allCountries.zip
   unzip -o $FILE.zip
   mv allCountries.txt $FILE
   rm $FILE.zip
fi

FILE=src/jvm/io/fsq/twofishes/indexer/data/downloaded/zip/allCountries.txt
if [ -f $FILE ];
then
   echo "File $FILE exists."
else
   curl -o $FILE.zip http://download.geonames.org/export/zip/allCountries.zip
   unzip -o $FILE.zip
   mv allCountries.txt $FILE
   rm $FILE.zip
fi

source src/jvm/io/fsq/twofishes/scripts/download-common.sh
./src/jvm/io/fsq/twofishes/scripts/extract-wiki-buildings.py
./src/jvm/io/fsq/twofishes/scripts/extract-adm.py
