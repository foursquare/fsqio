#!/bin/bash
set -e

function tempdir {
  mktemp -d "$1"/pants.trash.XXXXXX
}

# Be extra careful and verify that BUILD_ROOT is set before passing anything to rm -rf
if [ -z "${BUILD_ROOT+x}" ]; then
  echo -e "\nFAILURE! This script must be invoked from through the top-level upkeep script!\n"
  exit_with_failure "BUILD_ROOT environmental variable is unset!"
fi

rm -rf .pants.d/*
