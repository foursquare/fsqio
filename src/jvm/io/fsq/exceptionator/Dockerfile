FROM fsqio/fsqio

ENV DATA_DIR /data/exceptionator
RUN mkdir -p $DATA_DIR
VOLUME $DATA_DIR

EXPOSE 8080
ENTRYPOINT ["$BUILD_DIR/docker-mongo-hack.sh", "$DATA_DIR", "./pants", "run", "src/jvm/io/fsq/exceptionator"]
