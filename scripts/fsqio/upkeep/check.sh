#!/bin/bash

set -ea

# Schedule and run any required tasks.
# A 'required' string looks like: '1478544513: './upkeep force kestral' required kestral'
# Sort those strings based on the timestamp number, and then uses that order to run the corresponding tasks.

declare -a tasks=()

# Holds the list of the completed tasks.
completed_tasks=""
# The index pointer for an array that holds the contents of any "required" files as strings.
task_number=0

function check() {
  # This function looks for a task file and compares the associated required and current files.
  # If the contents of those files differ, the file is considered 'required' and is scheduled to be run.
  task_name="${1}"
  task_file=$(find_upkeep_file "tasks" "${task_name}.sh")
  upkeep_namespace=$(get_upkeep_namespace ${task_file})
  required_file="${upkeep_namespace}/required/${task_name}"

  # If a task script does not have a'required' file, exit with success since that task is considered "not required".
  if [ -f "${required_file}" ]; then
    current_file="${upkeep_namespace}/current/${task_name}"
    if [ -f "${current_file}" ]; then
      current=$(cat "${current_file}")
    fi
    required=$(cat "${required_file}")
    if [ "${current}" != "${required}" ]; then
      known_tasks[task_number]="${task_name}"
      tasks[task_number++]="${required}"
    fi
  fi
}

# If no task name was passed (the default behavior of upkeep) check all tasks in all available namespaces.
check_list=( "$@" )
check_list=${check_list:-$(all_task_names)}
for candidate in ${check_list[@]}; do
  check "${candidate}"
done

# Sort by timestamp.
IFS=$'\n' sorted=($(sort <<<"${tasks[*]}"))

# Print scheduled tasks to stdout. Probably there is a smarter way to do this, we redo the parsing in the next loop.
if [ "${#sorted[@]}" != 0 ]; then
  echo "Required Tasks:"
  for i in "${sorted[@]}"; do
    required_name=${i##* }
    echo -e "\t ${required_name}"
  done
fi

# Run the task in the above sorted order.
for i in "${sorted[@]}"; do
  # Strip the task name off the end of the required string.
  task_name=${i##* }
  ${BUILD_ROOT}/upkeep "${task_name}"
  completed_tasks="${completed_tasks} \n\t${task_name}"
done

# Print the report of completed tasks, incidentally still ordered.
if [ "${completed_tasks}" != "" ]; then
    echo -e "\nUpkeep complete after running: ${completed_tasks}"
fi
