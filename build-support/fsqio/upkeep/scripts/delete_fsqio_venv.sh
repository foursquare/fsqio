#!/bin/bash
# Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

set -eo pipefail

if [[ -z "${BUILD_ROOT+x}" ]]; then
  echo "Run this through upkeep run!"
  echo "e.g. ./upkeep run $0"
  exit 1
fi

set -eo pipefail

rm -rf "${FSQIO_VENV_BOOTSTRAP}"
