#!/bin/sh

ogrinfo  -where "$1" -geom=YES  $2 $3 | grep POLYGON  | cut -c 3- | perl -n -e "print \"$4\t\$_\";" >> src/jvm/io/fsq/twofishes/indexer/data/computed/polygons/99-manual.txt
