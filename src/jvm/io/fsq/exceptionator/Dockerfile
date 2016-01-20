FROM java:openjdk-8

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    mongodb \
    python-dev \
  && rm -rf /var/lib/apt/lists/*

ENV BUILD_DIR /app/fsqio
RUN mkdir -p $BUILD_DIR
COPY . $BUILD_DIR

WORKDIR $BUILD_DIR
RUN ./pants pom-resolve

ENV TEST_DATA_DIR /testdata
RUN mkdir -p $TEST_DATA_DIR && rm -rf $TEST_DATA_DIR/*
RUN $BUILD_DIR/docker/docker-mongo-hack.sh $TEST_DATA_DIR ./pants compile test :: && rm -rf $TEST_DATA_DIR

# TODO(dan): Everything before this point is the fsqio/fsqio image. Consider using that as the base.

ENV DATA_DIR /data/exceptionator
RUN mkdir -p $DATA_DIR
VOLUME $DATA_DIR

EXPOSE 8080
ENTRYPOINT ["$BUILD_DIR/docker-mongo-hack.sh", "$DATA_DIR", "./pants", "run", "src/jvm/io/fsq/exceptionator"]
