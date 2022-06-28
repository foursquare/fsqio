#!/bin/bash
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.
# Thanks to John Sirois for the pants bootstrapping functions.

set -eo pipefail

if [[ -z "${BUILD_ROOT+x}" ]]; then
  echo "BUILD_ROOT undefined! Expected to be called by a top-level script like 'pants' or 'upkeep'!"
  exit 1
fi

function which_python {
  found_python="$(which python2.7 || echo "")"
  if [[ -z "${found_python}" ]]
  then
    echo 'fsqio requires python2.7, please install it or set the $PYTHON env variable to point at a valid python executable'
    exit 1
  else
    echo "${found_python}"
  fi
}

# Transitive song and dance in order to enforce priority order of CANONICAL_PYTHON -> PYTHON -> $(which python2.7)
CANONICAL_PYTHON=${CANONICAL_PYTHON:-${PYTHON}}
PYTHON=${CANONICAL_PYTHON:-$(which_python)}

PANTS_BOOTSTRAP="${FSQIO_VENV_BOOTSTRAP}/bootstrap"

VENV_VERSION=15.0.1

VENV_PACKAGE="virtualenv-${VENV_VERSION}"
VENV_TARBALL="${VENV_PACKAGE}.tar.gz"

FOURSQUARE_REQUIREMENTS="${BUILD_ROOT}/3rdparty/python/requirements.txt"

# The high-level flow:
# 1.) Grab pants version from pants.ini or default to latest.
# 2.) Check for a venv via a naming/path convention and execute if found.
# 3.) Otherwise create venv and re-exec self.
#
# After that pants itself will handle making sure any requested plugins
# are installed and up to date.

function tempdir {
  mktemp -d "$1"/pants.XXXXXX
}

function bootstrap_venv {
  if [[ ! -d "${PANTS_BOOTSTRAP}/${VENV_PACKAGE}" ]]
  then
    (
      mkdir -p "${PANTS_BOOTSTRAP}" && \
      staging_dir=$(tempdir "${PANTS_BOOTSTRAP}") && \
      cd ${staging_dir} && \
      curl -LO https://pypi.python.org/packages/source/v/virtualenv/${VENV_TARBALL} && \
      tar -xzf ${VENV_TARBALL} && \
      ln -s "${staging_dir}/${VENV_PACKAGE}" "${staging_dir}/latest" && \
      mv "${staging_dir}/latest" "${PANTS_BOOTSTRAP}/${VENV_PACKAGE}"
    ) 1>&2
  fi
  echo "${PANTS_BOOTSTRAP}/${VENV_PACKAGE}"
}

function bootstrap_pants {
  pants_requirement="pantsbuild.pants"
  pants_version=$(
    grep -E "^[[:space:]]*pants_version" pants.ini 2>/dev/null | \
      sed -E 's|^[[:space:]]*pants_version[:=][[:space:]]*([^[:space:]]+)|\1|'
  )
  if [[ -n "${pants_version}" ]]
  then
    pants_requirement="${pants_requirement}==${pants_version}"
  else
    pants_version="unspecified"
  fi

  if [[ ! -d "${PANTS_BOOTSTRAP}/${pants_version}" ]]
  then
    (
      venv_path="$(bootstrap_venv)" && \
      staging_dir=$(tempdir "${PANTS_BOOTSTRAP}") && \
      "${PYTHON}" "${venv_path}/virtualenv.py" "${staging_dir}/install" && \
      source "${staging_dir}/install/bin/activate" && \
      [[ -n "${pants_version}" ]] && pip install "${pants_requirement}" && \
      pip install -r "${FOURSQUARE_REQUIREMENTS}" && \
      ln -s "${staging_dir}/install" "${staging_dir}/${pants_version}" && \
      mv "${staging_dir}/${pants_version}" "${PANTS_BOOTSTRAP}/${pants_version}"
    ) 1>&2
  fi
  echo "${PANTS_BOOTSTRAP}/${pants_version}"
}

pants_dir=$(bootstrap_pants) && \
export PANTSBINARY="${PANTSBINARY:-$pants_dir/bin/pants}"
