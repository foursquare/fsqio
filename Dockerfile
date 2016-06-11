FROM java:openjdk-8

ENV BUILD_DIR=/app/fsqio \
    TEST_DATA_DIR=/testdata \
    PANTS_COMPILE_ZINC_USE_NAILGUN=False \
    PANTS_COMPILE_ZINC_WORKER_COUNT=1

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    mongodb \
    python-dev \
  && rm -rf /var/lib/apt/lists/* \
  && mkdir -p $BUILD_DIR \
  && mkdir -p $TEST_DATA_DIR

COPY . $BUILD_DIR
WORKDIR $BUILD_DIR

RUN ./pants pom-resolve && \
    $BUILD_DIR/scripts/fsqio/docker/run-mongo.sh $TEST_DATA_DIR \
    ./pants --no-compile-zinc-use-nailgun compile test:: \
    && rm -rf $TEST_DATA_DIR
