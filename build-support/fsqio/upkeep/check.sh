#!/bin/bash

set -ea

# Schedule and run any required tasks.
# A 'required' string looks like: '1478544513: './upkeep force kestral' required kestral'
# Sort those strings based on the timestamp number, and then uses that order to run the corresponding tasks.

declare -a tasks=()
task_number=0

function check() {
  # This function looks for a task and compares the associated required and current files.
  # If the contents of those files differ, the file is considered 'required' and is scheduled to be executed.
  # usage: `check ${task_name}`
  #   e.g. for your_task.sh, `check your_task`
  task_name="${1}"
  task_file=$(find_upkeep_file "tasks" "${task_name}.sh") || exit_with_failure "No task named: ${task_name}"

  # A missing 'required' file is considered not required.
  required_file=$(find_upkeep_file "required" "${task_name}") || continue
  required=$(cat "${required_file}")

  current_file=$(get_current_path ${task_name})

  # The way of constructing and checking the current file is everywhere. A fix would be allow error catch from util.sh.
  if [[ -f "${current_file}" ]]; then
    mkdir -p $(dirname ${current_file})
    current=$(cat "${current_file}")
    if [ "${current}" != "${required}" ]; then
      tasks[task_number++]="${required}"
    fi
  else
    # A missing 'current' file is equivalent to mismatched contents- the task is considered required.
    tasks[task_number++]="${required}"
  fi
}

# If no task name was passed (the default behavior of upkeep) check all tasks in all available namespaces.
check_list=( "$@" )
check_list=${check_list:-$(all_task_names)}

for candidate in ${check_list[@]}; do
  check "${candidate}"
done

# Sort by timestamp. This is an array of full sentences - take care not to split on any whitespace if referencing!
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
completed_tasks=""
for i in "${sorted[@]}"; do
  # Strip the task name off the end of the required string.
  task_name=${i##* }

  # The check must not follow downstream, because it will infinitely recurse with the execute_task downstream functions.
  ${BUILD_ROOT}/upkeep "--no-downstream" "${task_name}"
  completed_tasks="${completed_tasks} \n\t${task_name}"
done

# Print the report of completed tasks, incidentally still ordered.
if [ "${completed_tasks}" != "" ]; then
    echo -e "\nUpkeep complete after running: ${completed_tasks}"
fi
