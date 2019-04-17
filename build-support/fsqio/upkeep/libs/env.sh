#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# Allow any passed pants or fsq.io to override the default config.
export DEPENDENCIES_ROOT="${DEPENDENCIES_ROOT:-$BUILD_ROOT/dependencies}"
export CURRENT_UNAME=$(uname -s)

export PYTHONPATH=${PYTHONPATH:-""}
export PYTHONIOENCODING="utf-8"

# Pants respects XDG_HOME settings, and will use that for the pants_bootstrapdir if it is set.
export FS_DOWNLOAD_CACHE="${FS_DOWNLOAD_CACHE:-${DEPENDENCIES_ROOT/package_cache}}"
export FSQ_RUN_AS_CI="${FSQ_RUN_AS_CI:-$JENKINS_HOME}"

cache_root="${XDG_CACHE_HOME:-${HOME}/.cache}"
export PANTS_BOOTSTRAPDIR=${PANTS_BOOTSTRAPDIR:-$cache_root/fsqio}
export PANTS_SUPPORTDIR=${PANTS_SUPPORTDIR:-$BUILD_ROOT/build-support}

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
      full_osx_vers=$(sw_vers -productVersion)
      export OS_ARCH=${OS_ARCH:-${full_osx_vers::5}}
      export OS_FULL_NAMESPACE="${OS_NAMESPACE}/${OS_ARCH}"
      ;;
    * )
      export OS_NAMESPACE="linux"
      # We do not currently support 32 bit or quantum machines.
      export OS_ARCH="x86_64"
      export OS_FULL_NAMESPACE="${OS_NAMESPACE}/${OS_ARCH}"
      ;;
esac

# Overrides for Fsq.io from the current Pants defaults due to more_itertools/pytest fiasco.
# Remove from Fsq.io once upgraded past this bug:
# https://github.com/pantsbuild/pants/issues/6282
export FS_SETUPTOOLS_VERS="30.0.0"
export FS_WHEEL_VERS="0.29.0"
export PANTS_PYTHON_SETUP_SETUPTOOLS_VERSION="${PANTS_PYTHON_SETUP_SETUPTOOLS_VERSION:-$FS_SETUPTOOLS_VERS}"
export PANTS_PYTHON_SETUP_WHEEL_VERSION="${PANTS_PYTHON_SETUP_WHEEL_VERSION:-$FS_WHEEL_VERS}"

# Override from internal version due to more-itertools bug with pex resolution. Fixed in upcoming Pants upgrade.
export PANTS_PYTEST_REQUIREMENTS=${PANTS_PYTEST_REQUIREMENTS:-"pytest==3.4.2"}

# Only include if not set elsewhere.
FSQIO_JVM_TEST_JUNIT_OPTIONS="+[\
  ' -Djava.util.logging.config.file=${BUILD_ROOT}/src/resources/io/fsq/props/logging.properties ' \
]"
export PANTS_JVM_TEST_JUNIT_OPTIONS=${PANTS_JVM_TEST_JUNIT_OPTIONS:-$FSQIO_JVM_TEST_JUNIT_OPTIONS}

export PANTS_IVY_CACHE_DIR=${PANTS_IVY_CACHE_DIR:-"$HOME/.pom2"}
export PANTS_IVY_IVY_PROFILE=${PANTS_IVY_IVY_PROFILE:-$PANTS_SUPPORTDIR/ivy/fsqio/fsqio.ivy.xml}
export PANTS_IVY_IVY_SETTINGS=${PANTS_IVY_IVY_SETTINGS:-$PANTS_SUPPORTDIR/ivy/fsqio/fsqio.ivysettings.xml}

