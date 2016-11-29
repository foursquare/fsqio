#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

set -ea

# Ensure this is only called through top-level upkeep wrapper so tasks can rely on the required environment variables.
if [ -z "${BUILD_ROOT+x}" ]; then
  exit_on_failure "BUILD_ROOT undefined! Only invoke upkeep through the top-level 'upkeep' script!"
fi

mkdir -p "${DEPENDENCIES_ROOT}"

function run_task() {
  # I think this is always passed as the task_name - using basename defensively for now.
  task_name=$(basename "$1")
  task_file=$(find_upkeep_file "tasks" "${task_name}.sh")
  echo -e "\nRunning ${task_name} upkeep..."
  "$task_file"

  # Update the 'current' file if the task happened to be required.
  namespace=$(get_upkeep_namespace ${task_file})
  required_file="${namespace}/required/${task_name}"
  current_file="${namespace}/current/${task_name}"

  if [ -f "${required_file}" ]; then
    mkdir -p $(dirname "${current_file}")
    requirement_string="${stamp}: './upkeep force ${forced_task}' required ${task_name}"
    cp "${required_file}" "${current_file}"
  fi

}

if [ "$0" = "$BASH_SOURCE" ]; then
  task_list=( $@ )
  for requested in ${task_list[@]}; do
    run_task "${requested}"
  done
  echo -e "Finished running upkeep tasks: ${task_list[@]}"
fi
