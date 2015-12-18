#!/bin/bash

DIR=$(dirname ${BASH_SOURCE[${#BASH_SOURCE[@]} - 1]})

source $DIR/run.sh

set -e

shopt -s nullglob # without this, empty $DIR would expand * to literal $DIR/*

ran=""
function check() {
  task=$(basename $1)
  required=$(cat $1)

  current=""
  if [ -f "$DIR"/current/"$task" ]; then
    current=$(cat $DIR/current/$task)
  fi

  if [ "$current" != "$required" ]; then
    echo "Running upkeep $task (current: '$current' vs required: '$required')..."
    run_task "$task" "$current"
    ran="$ran $task"
  fi
}

if [ "$1" != "" ]; then
  check $DIR/required/$1
else
  for req_file in $DIR/required/*; do
    check $req_file
  done
fi

if [ "$ran" != "" ]; then
    echo
    echo "$(date +%H:%M:%S) Finished running upkeep tasks: $ran"
fi
