#!/bin/sh

grep --no-filename $1 src/jvm/io/fsq/twofishes/indexer/data/private/polygons/* | perl -p -e "s/$1/$2/" >> src/jvm/io/fsq/twofishes/indexer/data/private/polygons/99-manual.txt
