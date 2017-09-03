#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

export PANTS_CONFIG_OVERRIDE=${PANTS_CONFIG_OVERRIDE:-"['pants.ini']"}

export JPOSTAL_VERSION='1.0'
# Internally, we use a tag from current master of Libpostal in order to get some gcc compilation bug fixes.
export LIBPOSTAL_VERSION='1.0.0.fs1a'
export LIBPOSTAL_DATA_VERSION='v1'
export JPOSTAL_BLOBS_VERSION="${LIBPOSTAL_VERSION}-${JPOSTAL_VERSION}"

# The script below runs the pants bootstrap task and exports PANTSBINARY. Basically a noop if the pants_version
# hasn't changed. This could be hooked more properly into upkeep but we are waiting on the need to arise.
source "${BUILD_ROOT}/scripts/fsqio/upkeep/libs/opensource-pants-env.sh"
