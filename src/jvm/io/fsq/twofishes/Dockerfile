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

ENV DATA_DIR /data/twofishes
ENV TWOFISHES_LATEST_PREBUILT_INDEX 2015-03-05.zip
RUN mkdir -p $DATA_DIR
RUN curl http://twofishes.net/indexes/revgeo/$TWOFISHES_LATEST_PREBUILT_INDEX > $DATA_DIR/$TWOFISHES_LATEST_PREBUILT_INDEX \
  && unzip $DATA_DIR/$TWOFISHES_LATEST_PREBUILT_INDEX -d $DATA_DIR \
  && rm $DATA_DIR/$TWOFISHES_LATEST_PREBUILT_INDEX

EXPOSE 8080
# TODO(dan): It'd be nice if the prebuilt index didn't have this funky subfolder.
CMD ["./src/jvm/io/fsq/twofishes/scripts/serve.py", "-p", "8080", "$DATA_DIR/2015-03-05-20-05-30.753698"]
