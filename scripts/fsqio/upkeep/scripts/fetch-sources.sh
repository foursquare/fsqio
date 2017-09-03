#!/bin/bash
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

# Bootstrap tarballs of official releases posted to 3rdparty GitHub repos.

# NOTE(mateo): Add new libraries or bump versions in the `files` array below.
#
# MUST be "ORG REPO VERSION" as a space-delineated string, i.e.
#  "foursquare fsqio v1.0.0"

files=( \
  "openvenues jpostal ${JPOSTAL_VERSION}" \
  "foursquare libpostal ${LIBPOSTAL_VERSION}" \
)

USAGE="\nUsage: ./upkeep run bootstrap_sources.sh"

function print_help() {
  echo -e "\Fetch Sources: Quick and dirty way to Fetch GitHub releases.\n"
  echo -e "\t This is only to be used to ease iteration loops or personal runs.\n"
  echo -e "\t If the result is meant for production, please upload the Fetchped files to the hosted location"
  echo -e "\t as configured in pants.ini [binaries] section.\n"
  echo -e "${USAGE}\n ./upkeep run fetch-sources.sh"
  exit
}

for arg in $@; do
  case "${arg}" in
    "--help"|"help"|"-h" )
      print_help
      exit
  esac
done

# Keep a list of successfully found/fetched files.
declare -a fetched_files

for i in "${files[@]}"; do
  arr=( $i )
  [[ ${#arr[@]} -eq 3 ]] || exit_with_failure \
    "The lib format is a string with a single space, e.g. 'ORG REPO VERSION'. (was: ${arr[@]})"

  ORGNAME=${arr[0]}
  LIBNAME=${arr[1]}
  LIBVERSION=${arr[2]}

  # Hosted blobs for this file are source code and so allowed to mock OS-specific file paths
  # If you need OS-specific downloads, you will need to add logic around $CURRENT_UNAME.
  OUTPUT_PATH="bin/${LIBNAME}/linux/x86_64/${LIBVERSION}/${LIBNAME}.tar.gz"
  DESTINATION_FILE="${DEPENDENCIES_ROOT}/${OUTPUT_PATH}"
  RELEASE_URL="${GITHUB}/${ORGNAME}/${LIBNAME}/archive/${LIBVERSION}.tar.gz"

  # Generally speaking, we want to re-execute the entire task when forced. But in this case, we are just caching
  # downloads, which are safely versioned and namespaced. So allowing a noop for a cached download seems proper.
  if [ ! -f "${DESTINATION_FILE}" ]; then
    response=$(curl -L -O --write-out %{http_code} "${RELEASE_URL}")
    if [[ ! $response -eq 200 ]]; then
      exit_with_failure "Was unable to curl ${RELEASE_URL}! Check your input or hosting!"
    fi

    DEST_DIR=$(dirname "${DESTINATION_FILE}")
    mkdir -p "${DEST_DIR}"
    mv "${LIBVERSION}.tar.gz" "${DESTINATION_FILE}"
  fi

  fetched_files=( ${fetched_files[@]} "${DESTINATION_FILE}" )
done

colorized_warn "Successfully Fetched:\n"
for i in ${fetched_files[@]}; do
  file_pair=( ${i} )
  colorized_warn "\t* ${file_pair[0]}\n"
done
