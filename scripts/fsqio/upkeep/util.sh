#!/bin/bash
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

# Librarys for common functions useful to the upkeep pipeline.

# Register any new upkeep roots here (but why?).
set -eoa pipefail

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
  echo -e " - ./upkeep\n\tCheck *all* tasks and run as required."
  echo -e " - ./upkeep tasks\n\tList available tasks."
  echo -e " - ./upkeep <tasks>...\n\tRun given tasks, required or not."
  echo -e " - ./upkeep run SCRIPT [<args>...] \n\tCheck upkeep, seed env, and execute './SCRIPT \${args[@]}'"
  echo -e "  \tSCRIPT can point to <upkeep_root>/scripts/\${SCRIPT}.sh or a full path relative to the build root."
  echo ""
  echo -e "Advanced"
  echo -e " - ./upkeep check [<tasks>...]\n\tCheck given tasks and run if required."
  echo -e " - ./upkeep force [<tasks>...]\n\tForce all users to run specified tasks upon consumption."
  echo -e "\t(Can include forcing specified 'downstream tasks', i.e. task dependencies)."
  echo -e " - ./upkeep task-list\n\tReturn space-delimited list of available tasks (suitable for tests/scripting)."
  echo ""
}

function print_all_tasks() {
  echo ""
  echo -e "Upkeep Tasks:"
  echo -e "\t Usage can be seen with \`./upkeep --help\`\n"
  echo "Available tasks:"
  task_list=$(all_task_names)
  for i in ${task_list[@]}; do
    echo -e "\t${i}"
  done
  echo ""
}

function all_matched_files() {
  # Glob files under upkeep subfolder. If no files match, returns empty string.
  # all_matched_files $action_type $regex
  echo $(find ${BUILD_ROOT}/scripts/*/upkeep/${1}/${2} -type f 2> /dev/null)
}

function all_task_names() {
  # Defaults to returning the task_name of all available tasks.
  # usage: get_task_names $regex
  glob=${1:-"*"}
  task_files=( $(all_matched_files "tasks" "${glob}") )

  # I wish this was better - having to make it work on multiple shells killed any hope of it reading clearly.
  for i in ${task_files[@]}; do
    tasks=(${tasks[@]} $(echo ${i##*/} | cut -d. -f1  ))
  done
  echo ${tasks[@]} | sort -u
}

function find_script() {
  script_name="${1}"
  if [ -z "${script_name}" ] || [[ "${script_name}" == "-h" ]] || [[ "${script_name}" == "--help" ]]; then
      print_help
      exit
  fi
  # Look for a matching file path and then checks for <foo>/upkeep/scripts/$1.sh.
  if [ -f "${script_name}" ]; then
    script_name="${BUILD_ROOT}/${script_name}"
  else
    script_name=$(find_upkeep_file "scripts" "${script_name}".sh)
  fi
  echo "${script_name}"
}

function find_upkeep_file() {
  # Ensure there is only one match for a given task.
  # usage: find_upkeep_file [tasks|required|current|etc] task_name'

  upkeep_action="${1}"
  file_name="${2}"
  found_matches=( $(all_matched_files "${upkeep_action}" "${file_name}") )

  case "${#found_matches[@]}" in
    0 )
      # TODO(mateo) Improve the "magic" path knowledge here. Should be extrapolated so it doesn't get stale.
      exit_with_failure "No match registered under scripts/<foo>/upkeep/${upkeep_action}: ${file_name}"
      ;;
    1 )
      echo "${found_matches[@]}"
      ;;
    * )
      red_output "ERROR! Multiple upkeep files found under that name: ${file_name}\n"
      for i in "${found_matches[@]}"; do
        exec echo -en "\t${i}\n"
      done
      exit_with_failure "Exactly one upkeep file may be registered under a given name!"
      ;;
  esac
}

function red_output() {
  echo -ne "[31;50m${1}[0m"
}

function print_output() {
  exec echo "$@"
}

function exit_with_failure() {
  echo -en $(red_output "UPKEEP FAILURE!\n") >&2
  echo -en $(red_output "$@\n") >&2
  exit -1
}
