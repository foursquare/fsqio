#!/bin/bash

DIR=$(dirname ${BASH_SOURCE[${#BASH_SOURCE[@]} - 1]})
what=$1
shift

downstream_tasks_file=$DIR/downstream-tasks/$what

if [ -f "$downstream_tasks_file" ]; then
  IFS=$'\n' read -d '' -r -a tasks < ${downstream_tasks_file}
  echo -e "[31;50mTask '$what' requires downstream tasks:[0m\n"
  # Run the original task first.
  tasks=($what ${tasks[@]})
else
  tasks=($what)
fi

buffer=10
timestamp=$(date +%s)
for task in ${tasks[@]}; do
    timestamp=$((timestamp + 10))
  # This "tasks" dir needs to be adjusted to work with fsqio.
  task_file=$DIR/tasks/${task}.sh
  if [ ! -f "$task_file" ]; then
    echo "[31;50mTask '$task' not found at $task_file![0m"
    exit 1
  else
    echo "$timestamp: './upkeep force "$what"' required $task"
    echo "$timestamp: './upkeep force "$what"' required $task" > $DIR/required/$task
    git add $DIR/required/$task
  fi
done
