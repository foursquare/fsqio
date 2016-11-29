#!/bin/bash

set -a

# The timestamp is used to determine task order at runtime.
# Tasks are separated from their dependent tasks by 10 and from independently requested tasks by 1000.
# It's a hack, you should Google an improvement.
# Task dependency DAGs are unconnected components with only outbound edges, could be toposorted.

stamp=$(date +%s)

# 'force' takes a single task as an argument and orders that task, along with any declared downstream tasks.
function force() {
  forced_task="$1"
  downstream_tasks_file=$(all_matched_files "downstream-tasks" ${forced_task})

  if [ -f "${downstream_tasks_file}" ]; then
    IFS=$'\n' read -d '' -r -a tasks < "${downstream_tasks_file}"
    red_output "Task '${forced_task}' requires downstream tasks:\n"
    red_output "\t$(echo ${tasks[@]})\n\n"

    for prospective in "${tasks[@]}"; do
      upkeep_namespace=$(get_upkeep_namespace ${downstream_tasks_file})
      task_file="${upkeep_namespace}/tasks/${prospective}.sh"

      if [ ! -f "${task_file}" ]; then
        # Note: All downstream tasks must share the same 'scripts/${foo}/upkeep/tasks' namespace.
        red_output "Task was not found at: "
        echo -en "${task_file}!\n"
        exit_with_failure "'upkeep force ${forced_task}' requested an invalid downstream task: '${prospective}'!"
      fi
    done
    # Force the original task first.
    tasks=( "${forced_task}" "${tasks[@]}" )
  else
    tasks=( "${forced_task}" )
  fi

  for task in ${tasks[@]}; do
    stamp=$(($stamp + 10))
    task_script=$(find_upkeep_file "tasks" "${forced_task}.sh")
    upkeep_namespace=$(get_upkeep_namespace ${task_script})

    # Print the requirement to stdout and also overwrite the task's required file.
    # This requirement string has semantic meaning and is parsed for scheduling purposes!
    # Please be aware that changing this string may require changes to the parsing logic in check.sh.
    requirement_string="${stamp}: './upkeep force "$forced_task"' required ${task}"
    echo "${requirement_string}" | tee "${upkeep_namespace}/required/${task}"
  done
}

# Timestamp value is exclusively used to determine execution order.
forced=( "$@" )
for forced in "${forced[@]}"; do
  stamp=$((stamp + 1000))
  force "${forced}"
done

# TODO(mateo): Instead of printing the requirement strings, this should print the final sorted task order.
