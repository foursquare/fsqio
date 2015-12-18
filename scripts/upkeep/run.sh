#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# Pass variables to children
set -a


# Please do NOT run this script directly, interact with the upkeep script instead.

DIR=$(dirname ${BASH_SOURCE[${#BASH_SOURCE[@]} - 1]})

mkdir -p "$DIR"/current

source "$DIR"/"$TASKS"/env.sh

function run_task() {
  task_script="$DIR"/"$TASKS"/$(basename $1).sh
  "$task_script" "$2" && if [ -f "$DIR"/required/$1 ]; then cp "$DIR"/required/$1 "$DIR"/current/$1; fi
}

for task in "$@"; do
  if [ "$0" = "$BASH_SOURCE" ]; then
    if [ $# -gt 1 ]; then
      echo "Running $task upkeep..."
    fi
    run_task "$task" "manually";
  fi
done

