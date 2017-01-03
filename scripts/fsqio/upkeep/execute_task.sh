#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

set -ea

# Ensure this is only called through top-level upkeep wrapper so tasks can rely on the required environment variables.
if [ -z "${BUILD_ROOT+x}" ]; then
  exit_on_failure "BUILD_ROOT undefined! Only invoke upkeep through the top-level 'upkeep' script!"
fi

mkdir -p "${DEPENDENCIES_ROOT}"

function run_required() {
  local task_name=$(basename "$1")
  local task_file=$(find_upkeep_file "tasks" "${task_name}.sh") || \
    exit_with_failure "Could not find an upkeep task for '${task_name}'"

  colorized_warn "\nRunning ${task_name} upkeep...\n"
  "$task_file"

  local required_path=$(find_upkeep_file "required" "${task_name}")

  # Update the 'current' file if the task happened to be required.
  if [ -f "${required_path}" ]; then
    local current_path=$(get_current_path ${required_path} ${task_name})
    mkdir -p $(dirname ${current_path})
    # Changing this string will invalidate all upkeep tasks and may break the ordering algorithm!
    local requirement_string="${stamp}: './upkeep force ${forced_task}' required ${task_name}"
    cp "${required_path}" "${current_path}"
  fi

}

function run_downstream() {
  # This is the most naive way to force a list of tasks along with their dependent tasks, while still maintaining order.
  # I tried to be clever but it spiraled out of control quite quickly. So I retreated. This is fine (:tire::fire:).
  downstream_args=( "$@" )
  all_downstream_chains=()

  for task_name in "${downstream_args[@]}"; do
    all_downstream_chains=( "${all_downstream_chains[@]}" $(get_task_and_downstream "${task_name}") )
  done
  # Dedupe and sorts into "${unique_tasks[@]}". Usually undesirable but check.sh parses and orders tasks so it works.
  IFS=$'\n' unique_tasks=($(sort -u <<<"${all_downstream_chains[*]}"))

  function unforce() {
    for forced in ${unique_tasks[@]}; do
      req=$(find_upkeep_file "required" "${forced}") || continue
      git checkout "${req}"

      if [[  "${1}" == "true" ]]; then
        cur=$(get_current_path ${req} ${forced})

        mkdir -p $(dirname ${cur})
        cp "${req}" "${cur}"
      fi
    done
    }
  # Reset the 'required' files on error or ctrl-c.
  trap "unforce" ERR INT

  # Force writes the resolved task order and check parses and schedules the task runs through run_required().
  "${BUILD_ROOT}/upkeep" "force" "${downstream_args[@]}"
  "${BUILD_ROOT}/upkeep" "check" "--no-downstream" "${unique_tasks[@]}"

  # Reset the required files and copy to current in order to indicate succesful run.
  unforce true
}

if [ "$0" = "$BASH_SOURCE" ]; then
  if [[ "${DOWNSTREAM_TASKS}" != "true" ]]; then
    # This is where the tasks are actually run - eventually the DOWNSTREAM_TASKS pipeline calls every task run here.
    task_list=( "$@" )
    for requested in ${task_list[@]}; do
      run_required "${requested}"
      echo -e "Upkeep task complete: ${requested}"
    done
    if [[ ${#task_list[@]} -gt 1 ]]; then
      echo -e "\nFinished upkeep tasks:"
      for completed in ${task_list[@]}; do
        echo -e "\t${completed}"
      done
    fi
  else
    # Downstream tasks was invoked - this calls back into upkeep but does not itself run tasks.
    args=( $@ )
    run_downstream "${args[@]}"
  fi
fi
