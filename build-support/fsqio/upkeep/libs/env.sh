#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# Allow any passed pants or fsq.io to override the default config.
export PANTS_CONFIG_OVERRIDE="${PANTS_CONFIG_OVERRIDE:-"['pants.ini']"}"
export DEPENDENCIES_ROOT="${BUILD_ROOT}/dependencies"
export CURRENT_UNAME=$(uname -s)

# JPostal defaults, used for our RPM that is currently just partially opensourced.
#
# The 1.0.0 Libpostal release has a gcc compile bug that broke compile on one of our supported OS.
# So this uses our fork of libpostal that has no code changes and simply tags the fixed master branch from openvenues.
# https://github.com/foursquare/libpostal/tree/v1.0.0.fs1a
export LIBPOSTAL_VERSION='1.0.0.fs1a'
export LIBPOSTAL_DATA_VERSION='v1'
export JPOSTAL_VERSION='1.0'
export JPOSTAL_BLOBS_VERSION="${LIBPOSTAL_VERSION}-${JPOSTAL_VERSION}"

# The script below runs the pants bootstrap task and exports PANTSBINARY. Basically a noop if the pants_version
# hasn't changed. This could be hooked more properly into upkeep but we are waiting on the need to arise.
source "${BUILD_ROOT}/build-support/fsqio/upkeep/libs/opensource-pants-env.sh"
