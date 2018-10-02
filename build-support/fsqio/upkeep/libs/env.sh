#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# Allow any passed pants or fsq.io to override the default config.
export DEPENDENCIES_ROOT="${BUILD_ROOT}/dependencies"
export CURRENT_UNAME=$(uname -s)

export PYTHONPATH=${PYTHONPATH:-""}
export PYTHONIOENCODING="utf-8"

CACHEDIR="${XDG_CACHE_HOME:-${HOME}/.cache}"
export FS_DOWNLOAD_CACHE="${FS_DOWNLOAD_CACHE:-${DEPENDENCIES_ROOT/package_cache}}"

# Stand-in that is used to set CI-only flags, gate console output, and toggle for unit tests.
export FSQ_RUN_AS_CI="${FSQ_RUN_AS_CI:-$JENKINS_HOME}"

# LIBPOSTAL and JPOSTAL
# These libraries are tagged and versioned in funny ways because the upstream library
# doesn't release packages and so feels no pain when being capricious with version names.
#
# The tag on github follows the pattern:
#    ${*POSTAL_VERSION}.${*POSTAL_RELEASE}
#
#  VERSION == The major.minor version from upstream
#  RELEASE == Foursquare versioning if we fork (which we do almost entirely to fix the versioning)
#  RELEASE_TAG == ${VERSION}.${RELEASE} and must match the tag of the library on Github.

export JPOSTAL_VERSION="1.1"
export JPOSTAL_RELEASE="alpha.fs1c"
export JPOSTAL_RELEASE_TAG="${JPOSTAL_VERSION}.${JPOSTAL_RELEASE}"

export LIBPOSTAL_VERSION="1.1"
export LIBPOSTAL_RELEASE="alpha.fs1a"
export LIBPOSTAL_RELEASE_TAG=${LIBPOSTAL_VERSION}.${LIBPOSTAL_RELEASE}

# The data files have no precise versioning upstream, simply ship with timestamp in a text file.
# The RELEASE is determined by the package-jpostal.sh script (pass --package-data) using the cksum that file.
export LIBPOSTAL_DATA_VERSION='v1'
export LIBPOSTAL_DATA_RELEASE="2217266661"

# These are native libs that are OS-dependent and built by the package-jpostal.sh script.
export JPOSTAL_BLOBS_VERSION="${LIBPOSTAL_RELEASE_TAG}-${JPOSTAL_RELEASE_TAG}"

case $CURRENT_UNAME in
    [Dd]arwin )
      export OS_NAMESPACE="mac"
      # This will break if we ever start caring about minor versions.
      export OS_ARCH=$(sw_vers -productVersion | sed "s:.[[:digit:]]*.$::g")
      export OS_FULL_NAMESPACE="${OS_NAMESPACE}/${OS_ARCH}"      ;;
    * )
      export OS_NAMESPACE="linux"
      # We do not currently support 32 bit or quantum machines.
      export OS_ARCH="x86_64"
      export OS_FULL_NAMESPACE="${OS_NAMESPACE}/${OS_ARCH}"
      ;;
esac

# Only include if not set elsewhere.
FSQIO_JVM_TEST_JUNIT_OPTIONS="+[\
  ' -Djava.util.logging.config.file=${BUILD_ROOT}/src/resources/io/fsq/props/logging.properties ' \
]"
export PANTS_JVM_TEST_JUNIT_OPTIONS=${PANTS_JVM_TEST_JUNIT_OPTIONS:-$FSQIO_JVM_TEST_JUNIT_OPTIONS}
