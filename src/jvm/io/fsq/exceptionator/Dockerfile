FROM fsqio/fsqio

ENV DATA_DIR /data/exceptionator
RUN mkdir -p $DATA_DIR
VOLUME $DATA_DIR

EXPOSE 8080
CMD ["/app/fsqio/docker/run-mongo.sh", "/data/exceptionator", "./pants", "run", "src/jvm/io/fsq/exceptionator"]
