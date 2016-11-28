#!/bin/bash

DIR=$(dirname ${BASH_SOURCE[${#BASH_SOURCE[@]} - 1]})

source $DIR/run.sh
set -e
shopt -s nullglob # without this, empty $DIR would expand * to literal $DIR/*

# This file reads 'required' files, with contents like "1478494889: './upkeep force homebrew' required fs-env"
# The number in the front implies required order - this script will sort them and run the task name at the end.

declare tasks=()

# Tracking an index so we can add whole sentences to the array.
task_number=0

function check() {
  task=$(basename $1)
  required=$(cat $1)

  current=""
  if [ -f "$DIR"/current/"$task" ]; then
    current=$(cat $DIR/current/$task)
  fi

  if [ "$current" != "$required" ]; then
    tasks[task_number++]="$required"
  fi
}

# Holds the names of the completed tasks.
ran=""

if [ "$1" != "" ]; then
  # If a task name was passed, only check that one task.
  check $DIR/required/$1
else
  # If no task name was passed (this is the default) check them all.
  for req_file in $DIR/required/*; do
    check $req_file
  done
fi

# Sort the required strings - sorts based on the timestamp.
IFS=$'\n' sorted=($(sort <<<"${tasks[*]}"))

# Output scheduled tasks. Probably there is a smarter way to do this, we redo the parsing in the next loop.
if [ "${#sorted[@]}" != 0 ]; then
  echo "Required Tasks:"
  for i in ${sorted[@]}; do
    task_name=${i##* }
    echo -e "\t $task_name"
  done
fi

for i in ${sorted[@]}; do
  # Strip the task name off the end of the required string.
  task_name=${i##* }
  echo "Running upkeep $task_name (required: '$i')..."
  run_task "$task_name" "$i"
  ran="$ran \n\t$task_name"
done
if [ "$ran" != "" ]; then
    echo
    echo -e "Finished running upkeep tasks: $ran"
fi
