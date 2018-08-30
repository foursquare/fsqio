#!/bin/bash
set -eax

# Be extra careful and verify that BUILD_ROOT is set before passing anything to rm -rf
if [[ -z ${BUILD_ROOT+x} ]]; then
  echo -e "\nFAILURE! This script must be invoked from through the top-level upkeep script!\n"
  echo "BUILD_ROOT environmental variable is unset!"
  exit -1
fi

# Directories to clean expandable in just this way. Try to be extra sure and not delete if unset.
export FSQ_CLEAN_DIRS=( ${FSQ_CLEAN_DIRS[@]} "${BUILD_ROOT}/.pants.d" )
if [[ ! -z ${FSQ_CLEAN_DIRS+x} ]]; then
   rm -rf ${FSQ_CLEAN_DIRS[@]}
fi

