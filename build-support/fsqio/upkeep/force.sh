#!/bin/bash

set -a

# The timestamp is used to determine task order at runtime.
# Tasks are separated from their dependent tasks by 10 and from independently requested tasks by 1000.
# It's a hack, you should implement an improvement.
# Task dependency DAGs are unconnected components with only outbound edges, could be toposorted.

stamp=$(date +%s)

function force() {
  forced_name="$1"
  task_script=$(find_upkeep_file "tasks" "${forced_name}.sh") || exit_with_failure "No task found for: ${forced_name}!"
  downstream_tasks_file=$(find_upkeep_file "downstream-tasks" ${forced_name})

  downstream_tasks=( $(get_task_and_downstream "${forced_name}") )
  if [[ "${#downstream_tasks[@]}" -gt 1 ]]; then
    colorized_warn "Task '${forced_name}' requires downstream tasks:\n"
    colorized_warn "\t$(echo ${downstream_tasks[@]})\n"
  fi

  for forced_task in "${downstream_tasks[@]}"; do
    task_script=$(find_upkeep_file "tasks" "${forced_task}.sh") || \
    exit_with_failure "'${forced_name}' required a downstream task that could not be found: '${forced_task}'"

    stamp=$(($stamp + 10))
    forced_required=$(find_upkeep_file "required" "${forced_task}") ||  \
      exit_with_failure  \
      "${task_script} registered a downstream task with no found 'required' file." "\tCheck ${downstream_tasks_file}"

    # This requirement string has semantic meaning and a schema that is parsed as part of the scheduler!
    requirement_string="${stamp}: './upkeep force "$forced_name"' required ${forced_task}"
    echo "${requirement_string}" | tee "${forced_required}"
  done
}

passed_tasks=( "$@" )
for forced in "${passed_tasks[@]}"; do
  stamp=$((stamp + 1000))
  force "${forced}"
done
