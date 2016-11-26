#!/bin/bash
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

# Librarys for common functions useful to the upkeep pipeline.

# Register any new upkeep roots here (but why?).
function get_upkeep_namespace() {
  if [[ "${1}" == *"scripts/foursquare"* ]]; then
    echo "${BUILD_ROOT}/scripts/foursquare/upkeep"
  elif [[ "${1}" == *"scripts/fsqio"* ]]; then
    echo "${BUILD_ROOT}/scripts/fsqio/upkeep"
  fi
}

function print_help() {
  echo ""
  echo -e "upkeep:\n\tManage dependencies and the development environment."
  echo ""
  echo "Usage:"
  echo -e "\tCommands that accept a [<tasks>...] list expect it as a space-delimited string.\n"
  echo -e "General"
  echo -e " - \`./upkeep\`\n\tCheck every task and run if required."
  echo -e " - \`./upkeep tasks\`\n\tList available tasks."
  echo -e " - \`./upkeep [<tasks>...]\`\n\tRun given tasks, required or not."
  echo ""
  echo -e "Advanced"
  echo -e " - \`./upkeep check [<tasks>...]\`\n\tCheck given tasks and run if required."
  echo -e " - \`./upkeep force [<tasks>...]\`\n\tForce all users to run specified tasks upon consumption."
  echo -e "\t(Can include forcing specified 'downstream tasks', i.e. task dependencies)."
  echo -e " - \`./upkeep task-list\`\n\tReturn space-delimited list of available tasks (suitable for tests/scripting)."
  echo ""
}

function print_all_tasks() {
  echo ""
  echo -e "Available Upkeep Tasks:"
  echo -e "\t Usage can be seen with \`./upkeep --help\`\n"
  echo "Available tasks:"
  task_list=( $(all_task_names) )
  for i in ${task_list[@]}; do
    echo -e "\t${i}"
  done
  echo ""
}

function all_matched_files() {
  # Glob files under upkeep subfolder. If no files match, returns empty string.
  # all_matched_files $action_type $regex
  echo $(find ${BUILD_ROOT}/scripts/*/upkeep/$1/$2 2> /dev/null)
}

function all_task_names() {
  # Defaults to returning the task_name of all available tasks.
  # usage: get_task_names $regex
  glob=${1:-"*"}
  echo $(all_matched_files "tasks" "${glob}" | xargs basename | grep -v "tasks" | cut -d. -f1 | sort)
}

function validate_task() {
  # Ensure there is only one match for a given task.
  # usage: validate_task 'task_name'
  found_matches=( $(all_matched_files "tasks" "${1}.sh") )
  case "${#found_matches[@]}" in
    0 )
      exit_with_failure "Cannot find upkeep task: ${1}"
      ;;
    1 )
      # Consuming this string swallows the error-state, it needs proper handling before that would work.
      # echo "${found_matches[@]}"
      ;;
    * )
      red_output "ERROR! Multiple tasks found under task name: ${1}\n"
      for i in "${found_matches[@]}"; do
        echo -en "\t${i}\n"
      done
      echo ""
      exit_with_failure "Exactly one task file may be registered under a task name."
      ;;
  esac
}

# TODO(mateo): Make generic for color/non-color, which would trivially add support for --quiet mode.
function red_output() {
  echo -ne "[31;50m${1}[0m"
}

function exit_with_failure() {
  echo -e $(red_output "UPKEEP FAILURE!\n") >&2
  echo -e $(red_output "$@\n") >&2
  exit -1
}
