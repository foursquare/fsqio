#!/bin/bash
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

set -ea

# Compiles and packages both libpostal and jpostal on a per-OS basis. The compiled blobs are all packaged into
# a single level tarball under dist. The namespacing is significant for Pants purposes - a separate upkeep script
# consumes the tarballs from a hosted location and puts them on the repl's library.path.

# To update jpostal/libpostal blobs:
#     * adjust the VERSIONS in the env.sh file
#     * compile on linux and OS box
#     * upload the tarball to hosting preserving the namespacing here.
#       -  for internal usage, at ${FS_ARTIFACT_DEV_BUCKET}/pants/fs-bootstrap/bin/jpostal_blobs/<etc>

libpostal_tar=$(fetch_github_release "foursquare libpostal ${LIBPOSTAL_RELEASE_TAG} tar.gz")
[[ -e "${libpostal_tar}" ]] || exit_with_failure "${libpostal_tar}"

jpostal_tar=$(fetch_github_release "foursquare jpostal ${JPOSTAL_RELEASE_TAG} tar.gz")
[[ -e "${jpostal_tar}" ]] || exit_with_failure "${jpostal_tar}"

# Any environmental variables not defined here are being defined in env.sh.
DIST_DIR="${BUILD_ROOT}/dist/libpostal_packages"

JPOSTAL_ROOT="${DIST_DIR}/jpostal_blobs/${OS_NAMESPACE}/${OS_ARCH}/${JPOSTAL_BLOBS_VERSION}"
BLOBS_NAME='jpostal_blobs'
DATA_PACKAGE_NAME="libpostal_data"

BLOBS_FILE="${BLOBS_NAME}.tar.gz"
BLOBS_OUT="${JPOSTAL_ROOT}/${BLOBS_FILE}"
WORKROOT="${DEPENDENCIES_ROOT}/jpostal_outdir"

mkdir -p "${WORKROOT}" "${DIST_DIR}" "${JPOSTAL_ROOT}"

case "${OS_NAMESPACE}" in
  "mac" )
    MAKE="${DEPENDENCIES_ROOT}/homebrew/Library/Homebrew/shims/mac/super/make"
    ;;
  * )
    MAKE="make"
    ;;
esac

function print_help() {
  echo ""
  echo -e "Options"
  echo -e " --help\n\t Print this message."
  echo -e " --package-data\n\t Additionally bootstrap and package the libpostal training data."
  echo ""
  echo -e "\nPackage jpostal and libpostal blobs for use inside Foursquare.web.\n"
  echo -e "\nThis outputs files in dist under jpostal blobs. If updating, please upload them to bodega.\n"
  echo -e "\nEvery supported OS needs to be compiled for with this, or taken from the existing RPM.\n"
  echo -e "Usage:\n - ./upkeep run package-jpostal.sh"
  exit
}

declare -a libpostal_args=()

for arg in $@; do
  case "${arg}" in
    "--help"|"help"|"-h" )
      print_help
      ;;
    "--package-data" )
      SHOULD_PACKAGE_DATA=1
      ;;
  esac
done

# Holds the list of created packages for reporting to stdout.
declare -a packaged_archives=()

workdir=$(tempdir $WORKROOT)
output_dir="${workdir}/output"
datadir_name="postaldata"
datadir="${workdir}/${datadir_name}"
mkdir -p "${output_dir}" "${datadir}"

tar xzf "${jpostal_tar}" -C "${workdir}/"
tar xzf "${libpostal_tar}" -C "${workdir}/"

# Allow the workdir to be version-agnostic, done this way b/c libpostal flip-flops a bit between 1.0.0 and v1.0.0.
mv -f "${workdir}/libpostal"* "${workdir}/libpostal"
mv -f "${workdir}/jpostal"* "${workdir}/jpostal"

cd "${workdir}/libpostal/"

"${workdir}/libpostal/bootstrap.sh"
export PKG_CONFIG_PATH="${output_dir}"
export LD_CONFIG_PATH="${output_dir}"

# TODO(mateo): Break the data download into a separate script - it is slow as hell
# and the update cycle is decoupled from the binaries.

# The initial configure sets a datadir but disables the actual download. If --package-data was passed,
# the files are downloaded by direct invocation below. This is due to a quirk in the homegrown downloader
# used by libpostal: https://github.com/openvenues/libpostal/issues/310#issuecomment-390771313
"${workdir}/libpostal/configure" \
  --with-pic \
  --prefix=$(pwd)/../output \
  --libdir=$(pwd)/../output  \
  --includedir=$(pwd)/../output \
  --enable-static \
  --disable-shared \
  --datadir=${datadir} \
  --disable-data-download


if [[ -n "${SHOULD_PACKAGE_DATA}" ]]; then
  # mkdir ${datadir}/postaldata
  ./src/libpostal_data download all ${datadir}/libpostal
fi

"${MAKE}"
"${MAKE}" install

if [[ -n "${SHOULD_PACKAGE_DATA}" ]]; then
  #   * data_version is stable and appears to version the schema
  #   * last_updated file simply holds a timestamp.
  #
  # The versioning scheme simply hashes the timestamp (into hex) and appends it to the data_version.

  data_namespace="postaldata"
  downloaded_data_root="${datadir}/${data_namespace}"
  training_data_root="${datadir}/libpostal"

  data_version=$(cat "${training_data_root}/data_version")
  last_updated_hash=$(cksum "${training_data_root}/last_updated" | cut -d " " -f 1)

  data_output_root="${DIST_DIR}/${DATA_PACKAGE_NAME}/${OS_NAMESPACE}/${OS_ARCH}/${data_version}.${last_updated_hash}"
  mkdir -p "${data_output_root}"
  output_data_archive="${data_output_root}/${DATA_PACKAGE_NAME}.tar.gz"

  # Symlink to the historical directory name in order to maintain cross-compat.
  rm -f "${downloaded_data_root}"
  mv "${training_data_root}" "${downloaded_data_root}"
  colorized_warn "Creating data package (This takes awhile!)...\n"

  rm -rf "${output_data_archive}"
  tar czf "${output_data_archive}" -C "${datadir}" "${data_namespace}"
  packaged_archives=( ${packaged_archives[@]} "${output_data_archive}")
fi

mv -f "${output_dir}/pkgconfig/"* "${output_dir}/"
rm -rf "${output_dir}/lib" "${output_dir}/bin" "${output_dir}/include" "${output_dir}/pkgconfig" "${datadir}"

cd "${workdir}/jpostal/"
"${workdir}/jpostal/bootstrap.sh"
"${workdir}/jpostal/configure" \
 --includedir="${output_dir}" \
 --libdir=$(pwd)/../output  \
 --with-pic \
 --prefix="${output_dir}"
"${workdir}/jpostal/gradlew" assemble

mv -f "${workdir}/jpostal/src/main/jniLibs/"* "${output_dir}"
rm -rf "${BLOBS_OUT}"
tar czf "${BLOBS_OUT}" -C "${output_dir}" $(ls "${output_dir}")
packaged_archives=( ${packaged_archives[@]} "${BLOBS_OUT}")

colorized_warn "Packaged bundles:\n"
for i in ${packaged_archives[@]}; do
  colorized_warn "\t* ${i}\n"
done
colorized_warn "\nFor local use, copy the folders under ${DIST_DIR} to ${PANTS_BOOTSTRAP}/bin"
colorized_warn "To publish for production use, please upload to hosting\n"
