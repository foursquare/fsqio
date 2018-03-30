#!/bin/bash
# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

set -eo pipefail

# This needs to be much better.
function print_help() {
  echo -e "\nDownloads a linux or platform independent source package."
  echo -e "  USAGE: ./upkeep run fetch_remote_source.sh DEST NAMESPACE VERSION EXT [BASEDIR]"
  echo -e "\t* DEST is the file destination for the unpacked archive."
  echo -e "\t* [BASEDIR] is an optional prefix path to strip from the unpacked archive.\n"
  echo -e "e.g. ./upkeep run fetch_remote_source.sh mydir/place libhadoop 1.3 tar.gz\n"
  exit
}

[[ -d $1 ]] || exit_with_failure "Destination dir must exist!"
# Calls the download_and_extract function in the fetcher.sh
(
  [[ $# -gt 4 ]] || print_help && download_and_extract $@ \
) || exit_with_failure "Failed fetch"
