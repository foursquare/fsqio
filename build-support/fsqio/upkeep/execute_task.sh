#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

set -a

# Ensure this is only called through top-level upkeep wrapper so tasks can rely on the required environment variables.
if [ -z "${BUILD_ROOT+x}" ]; then
  exit_with_failure "BUILD_ROOT undefined! Only invoke upkeep through the top-level 'upkeep' script!"
fi

mkdir -p "${DEPENDENCIES_ROOT}"

function clean_on_error() {
  if [[ -f "${1}" ]]; then
    rm "${1}"
  fi
  colorized_warn "\t Removing the current file for: ${1}"
  exit_with_failure "${2}"
  # Not printing the exit_with_failure message because this should come from the task.
  # But exiting with an error code to protect any misconfigured tasks from reporting inaccurate success.
}

function run_actual_task() {
  local task_file="$1"
  local current_path="$2"

  trap "clean_on_error ${current_path} ${task_file}" ERR INT
  "$task_file"
}

function run_required() {
  local task_name=$(basename "$1")
  local task_file=$(find_upkeep_file "tasks" "${task_name}.sh")
  local required_path=$(find_upkeep_file "required" "${task_name}")
  local current_path="$(get_current_path ${task_name})"
  colorized_warn "\nRunning ${task_name} upkeep...\n"

  if [[ -f "${task_file}" ]]; then
    run_actual_task "$task_file" "${current_path}"
  else
   print_all_tasks
   exit_with_failure "Could not find an upkeep task for '${task_name}'"
  fi

  # Update the 'current' file if the task happened to be required.
  if [ -f "${required_path}" ]; then
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
      # If a new task is added, or the upkeep required files are relocated for some reason, this exits with an error.
      git checkout -q "${req}" &> /dev/null || true

      if [[  "${1}" == true ]]; then
        cur=$(get_current_path ${forced})

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
    if [[ -z "${FSQWORKING_XCODE}" ]]; then
      # If XCode is validated, set a env to disable future checks that invocation.
      validate_xcode && export FSQ_WORKING_XCODE="True"
    fi
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
