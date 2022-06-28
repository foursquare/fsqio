#!/bin/bash
# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

set -eao pipefail

# Bootstrap tooling that is hosted in a programmatically contstructable pattern.

function cached_download() {
  # usage: cached_download FILE_URL EXPECTED_FILE CACHE_FILE_PATH
  # NOTE: Moves (atomically) the fetched file to a cached location. So you need to wipe the cache to rebootstrap!
  local url="${1}"
  local expected_filename="${2}"
  local cache_path="${3}"
  local cachedir=$(dirname "${cache_path}")

  mkdir -p "${FS_TEMP_EXTRACTDIR}"
  mkdir -p "${cachedir}"

  # Download to a tmpdir and move to cache only upon success.
  local fetchdir=$(tempdir "${FS_TEMP_EXTRACTDIR}" "fetch.${expected_filename}")
  local download_path="${fetchdir}/${expected_filename}"

  trap "wipe_workspace ${FS_TEMP_EXTRACTDIR} 3" ERR INT
  if [[ ! -e "${cache_path}" ]]; then
    # Homebrew cannot use brew install wget to bootstrap itself.
    # So we allow this to fall back to curl in localdev. Add wget/curl option.
    _fetch_failed=""
    if [[ ${FSQ_RUNNING_ON_OSX} == "True" ]] && [[ ! -f ${FS_WGET_BINARY} ]]; then
      (curl "-fL${FSQ_CURL_PROGRESS}" ${FS_CURL_EXTRA_PARAMS} "${url}" >"${download_path}") || _fetch_failed="True"
    else
      (${FS_WGET_BINARY} ${FS_WGET_EXTRA_PARAMS} -nc -O ${download_path} ${url}) || _fetch_failed="True"
    fi
    (
      [[ -z ${_fetch_failed} ]] &&
        [[ -e "${download_path}" ]] &&
        mv -f "${download_path}" "${cache_path}"
    ) || (echo "${url}" && exit -9)
  fi
  if [[ -e "${cache_path}" ]]; then
    echo "${cache_path}"
  else
    echo "${url}"
  fi
  rm -rf "${FS_TEMP_EXTRACTDIR}"
}

function fetch_github_release() {
  # usage: `fetch_github_release ${ORG} ${REPO} ${VERSION} ${PACKAGE_EXTENSION}`
  #    e.g. `fetch_github_release pantsbuild pants release_1.3.0 tar.gz"
  #
  # This will download https://github.com/pantsbuild/pants/archive/release_1.3.0.tar.gz
  # This fetch is treated as cached once the expected file exists - so it must be deleted before it will redownload!
  local USAGE="Usage: fetch_github_release ORG REPO VERSION PACKAGE_EXTENSION"
  local args=($@)
  [[ ${#args[@]} -gt 3 ]] || echo "fetch_github_release ${USAGE}"

  local org="${args[0]}"
  local repo="${args[1]}"
  local version="${args[2]}"
  local ext="${args[3]:+.${args[3]}}"

  local filename="${repo}-${version}${ext}"
  # Github pattern is very hard to grok - seems priority was saving characters.
  # The artifact url points to "version.ext" but the downloaded file is repo-version.ext.
  local fetch_url="${GITHUB}/${org}/${repo}/archive/${version}${ext}"
  local cache_path="${FS_DOWNLOAD_CACHE}/${org}/${repo}/${version}/${filename}"
  echo $(cached_download "${fetch_url}" "${filename}" "${cache_path}")
}

function fetch_remote_source() {
  # Fetch OS-independent source packages from hosted root.
  #
  # usage: `fetch_remote_source ROOT_URL NAMESPACE VERSION [PACKAGE_EXTENSION] [OS_NAME] [OS_ARCH]`

  local USAGE="Usage: fetch_remote_source HOST_PATH NAMESPACE VERSION [PACKAGE_EXTENSION] [OS_NAME] [OS_ARCH]"
  local args=($@)
  [[ ${#args[@]} -gt 2 ]] || echo "fetch_remote_source.sh ${USAGE}"

  local host="${args[0]}"
  local namespace="${args[1]}"
  local version="${args[2]}"
  # NOTE(mateo): This will add the period if an extension was passed.
  local ext="${args[3]:+.${args[3]}}"
  # Default to linux/x86_64 as our proxy for OS-independent source packages.
  local os="${args[4]:-"linux"}"
  local arch="${args[5]:-"x86_64"}"

  local filename="${namespace}${ext}"
  local file_namespace="${namespace}/${os}/${arch}/${version}/${filename}"
  local fetch_url="${host}/${file_namespace}"
  local cache_path="${FS_DOWNLOAD_CACHE}/${file_namespace}"
  echo $(cached_download "${fetch_url}" "${filename}" "${cache_path}")
}

function fetch_remote_binary() {
  # usage: `fetch_remote_binary ROOT_URL NAMESPACE VERSION  [PACKAGE_EXTENSION] [linux|mac] [10.${x}|x86_64]`
  #    e.g. `fetch_remote_source https://binaries.pantsbuild.org cmake 3.9.5 tar.gz
  #    fetches: https://binaries.pantsbuild.org/bin/cmake/mac/10.13/3.9.5/cmake.tar.gz
  echo $(fetch_remote_source $@ "${OS_NAMESPACE}" "${OS_ARCH}")
}

function extract() {
  # usage: extract NAME ARCHIVE [EXTRACT_ROOT]
  [[ $# -lt 2 ]] && exit 2
  libname=$1
  fetched=$2
  scratch="${3:-$FS_TEMP_FETCHROOT/scratch_space/$libname}"

  mkdir -p "${scratch}"
  local extractdir=$(tempdir "${scratch}" "${libname}.extracted")

  # Sanity check that we do have the archive and then unpack.
  if [[ -e "${fetched}" ]]; then
    tar xf "${fetched}" -C "${extractdir}"
  else
    echo "${fetched}"
    exit 2
  fi
  echo "${extractdir}"
}

function relocate() {
  # NOTE(mateo): This does not raise an error if any of that chain fails - should consider threading a catch through.
  src="$1"
  dest="$2"
  [[ $# -eq 2 ]] && [[ -e "${src}" ]] && [[ -e "${dest}" ]] && mv -f "${src}"/* "${dest}/"
}

function download_and_extract() {
  # tarball-specific atomic extractor.

  # usage: download_and_extract DESTINATION_DIR LIBNAME VERSION EXTENSION [BASEDIR]
  # The optional basedir are paths to prune off the top of the unpacked archive.
  # e.g. "native_libs" as the BASEDIR would
  #    - unpack the archive to DESTINATION_DIR
  #    - relocate all files under the 'native_libs' path directly under DESTINATION_DIR
  [[ $# -lt 2 ]] && exit 2
  local destination="${1}"
  shift
  mkdir -p "${destination}"

  # Catch errors and wipe destination dir (already a tempdir but just in case)
  trap "wipe_workspace ${destination} 2" ERR INT

  local libname="${1}"
  local libversion="${2}"
  local ext="${3}"

  # If the archive has undesirable directory padding, copy starting after this basedir.
  if [[ "${4}" != linux ]] && [[ "${4}" != mac ]]; then
    # This API has been broken, this proves it. Break out the relocate into a separate function.
    local archive_basedir="${4}"
    shift
  fi
  local os_namespace="${4:-$OS_NAMESPACE}"
  local arch="${5:-$OS_ARCH}"

  # Find the corresponding archive, which may be cached.
  # If the fetcher exits non-zero, the attempted URL is returned.
  local fetch_args=("${libname}" "${libversion}" "${ext}" "${os_namespace}" "${arch}")
  local fetched=$(fetch_remote_source "${FS_REMOTE_SOURCES_URL}" ${fetch_args[@]}) || (echo "${fetched}" && exit 2)

  # Upon failure, the return value of fetched is designed to return the URL that failed to resolve.
  [[ -e "${fetched}" ]] || (colorized_error "Download failed: ${fetched}" && exit 6)
  local extractdir=$(extract "${libname}" "${fetched}")

  relocate "${extractdir}/${archive_basedir}" "${destination}"
  echo "${destination}"
}
