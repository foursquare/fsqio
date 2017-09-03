#!/bin/bash
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

set -e

# Compiles and packages both libpostal and jpostal on a per-OS basis. The compiled blobs are all packaged into
# a single level tarball under dist. The namespacing is significant for Pants purposes - a separate upkeep script
# consumes the tarballs from a hosted location and puts them on the repl's library.path.

# To update jpostal/libpostal blobs:
#     * adjust the VERSIONS in the env.sh file
#     * compile on linux and OS box
#     * upload the tarball to bodega preserving the namespacing here.
#       -  for internal usage, at bodega at /data/appdata/bodega/4sq-dev/pants/fs-bootstrap/bin/jpostal_blobs/<etc>


# There is no official release for libpostal 1.0.0 that compiles on our devbox's gcc.
# This release is current upstream master. Please unfork by using an upstream release and then remove this check.
if [[ ! "${LIBPOSTAL_VERSION}" == "1.0.0.fs1a" ]]; then
  exit_with_failure "Please try and consume the upstream libpostal release from openvenues: $0"
fi

"${BUILD_ROOT}/upkeep" run fetch-sources.sh

# Any environmental variables not defined here are being defined in env.sh.
DIST_DIR="${BUILD_ROOT}/dist/jpostal_blobs/${OS_NAMESPACE}/x86_64/${JPOSTAL_BLOBS_VERSION}"

BLOBS_NAME='jpostal_blobs'
BLOBS_FILE="${BLOBS_NAME}.tar.gz"
BLOBS_OUT="${DIST_DIR}/${BLOBS_FILE}"
WORKROOT="${DEPENDENCIES_ROOT}/jpostal_outdir"

case "${OS_NAMESPACE}" in
  "mac" )
    MAKE="${DEPENDENCIES_ROOT}/homebrew/Library/Homebrew/shims/super/make"
    ;;
  * )
    MAKE="make"
    ;;
esac

function print_help() {
  echo -e "\nPackage jpostal and libpostal blobs for use inside Foursquare.web.\n"
  echo -e "\nThis outputs files in dist under jpostal blobs. If updating, please upload them to bodega.\n"
  echo -e "\nEvery supported OS needs to be compiled for with this, or taken from the existing RPM.\n"
  echo -e "Usage:\n - ./upkeep run package-jpostal.sh"
  exit
}

mkdir -p "${WORKROOT}"
mkdir -p "${DIST_DIR}"
workdir=$(tempdir $WORKROOT)
output_dir="${workdir}/output"
mkdir -p "${output_dir}"

tar xzf "${DEPENDENCIES_ROOT}/bin/jpostal/linux/x86_64/${JPOSTAL_VERSION}/jpostal.tar.gz" -C "${workdir}/"
tar xzf "${DEPENDENCIES_ROOT}/bin/libpostal/linux/x86_64/${LIBPOSTAL_VERSION}/libpostal.tar.gz" -C "${workdir}/"

# Allow the workdir to be version-agnostic, done this way b/c libpostal flip-flops a bit between 1.0.0 and v1.0.0.
mv "${workdir}/libpostal"* "${workdir}/libpostal"
mv "${workdir}/jpostal"* "${workdir}/jpostal"

cd "${workdir}/libpostal/"

"${workdir}/libpostal/bootstrap.sh"
export PKG_CONFIG_PATH="${output_dir}"
export LD_CONFIG_PATH="${output_dir}"

"${workdir}/libpostal/configure" \
  --with-pic \
  --prefix=$(pwd)/../output \
  --libdir=$(pwd)/../output  \
  --includedir=$(pwd)/../output  \
  --disable-data-download \
  --enable-static \
  --disable-shared
"${MAKE}"
"${MAKE}" install

# pkg-config packages output to FWICT is a uncofigurable output. Moving them to a joint output and removing the rest.
mv "${output_dir}/pkgconfig/"* "${output_dir}/"
rm -rf "${output_dir}/lib" "${output_dir}/bin" "${output_dir}/include" "${output_dir}/pkgconfig"

cd "${workdir}/jpostal/"
"${workdir}/jpostal/bootstrap.sh"
"${workdir}/jpostal/configure" \
 --includedir="${output_dir}" \
 --libdir=$(pwd)/../output  \
 --with-pic \
 --prefix="${output_dir}"
"${workdir}/jpostal/gradlew" assemble
mv "${workdir}/jpostal/src/main/jniLibs/"* "${output_dir}"
tar czf "${BLOBS_OUT}" -C "${output_dir}" $(ls "${output_dir}")

colorized_warn "Jpostal blobs are in a tarball at\n\t* ${BLOBS_OUT}\nPlease upload to hosting\n\n"
