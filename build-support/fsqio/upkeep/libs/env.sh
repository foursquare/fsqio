#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# Allow any passed pants or fsq.io to override the default config.
export PANTS_CONFIG_FILES="${PANTS_CONFIG_FILES:-"['pants.ini']"}"
export DEPENDENCIES_ROOT="${BUILD_ROOT}/dependencies"
export CURRENT_UNAME=$(uname -s)

CACHEDIR="${XDG_CACHE_HOME:-${HOME}/.cache}"
export PANTS_BOOTSTRAPDIR="${PANTS_BOOTSTRAPDIR:-$CACHEDIR/fsqio}"

export FS_DOWNLOAD_CACHE="${FS_DOWNLOAD_CACHE:-${DEPENDENCIES_ROOT/package_cache}}"

# Stand-in that is used to set CI-only flags, gate console output, and toggle for unit tests.
export FSQ_RUN_AS_CI="${FSQ_RUN_AS_CI:-$JENKINS_HOME}"

# JPostal defaults, used for our RPM that is currently just partially opensourced.
#
# The 1.0.0 Libpostal release has a gcc compile bug that broke compile on one of our supported OS.
# So this uses our fork of libpostal that has no code changes and simply tags the fixed master branch from openvenues.
# https://github.com/foursquare/libpostal/tree/v1.0.0.fs1a
export LIBPOSTAL_VERSION='1.0.0.fs1a'
export LIBPOSTAL_DATA_VERSION='v1'
export JPOSTAL_VERSION='1.0'
export JPOSTAL_BLOBS_VERSION="${LIBPOSTAL_VERSION}-${JPOSTAL_VERSION}"

case $CURRENT_UNAME in
    [Dd]arwin )
      export OS_NAMESPACE="mac"
      # This will break if we ever start caring about minor versions.
      OS_ARCH=$(sw_vers -productVersion | sed "s:.[[:digit:]]*.$::g")
      export OS_FULL_NAMESPACE="${OS_NAMESPACE}/${OS_ARCH}"
      ;;
    * )
      export OS_NAMESPACE="linux"
      # We do not currently support 32 bit or quantum machines.
      export OS_ARCH="x86_64"
      export OS_FULL_NAMESPACE="${OS_NAMESPACE}/${OS_ARCH}"
      ;;
esac

export PANTS_JVM_TEST_JUNIT_OPTIONS="+[\
  ' -Djava.util.logging.config.file=${BUILD_ROOT}/src/resources/io/fsq/props/logging.properties ' \
]"

# The script below runs the pants bootstrap task and exports PANTSBINARY. Basically a noop if the pants_version
# hasn't changed. This could be hooked more properly into upkeep but we are waiting on the need to arise.
source "${BUILD_ROOT}/build-support/fsqio/upkeep/scripts/opensource-pants-env.sh"
