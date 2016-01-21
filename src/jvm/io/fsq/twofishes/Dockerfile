FROM fsqio/fsqio

ENV DATA_DIR /data/twofishes
ENV TWOFISHES_LATEST_PREBUILT_INDEX 2015-03-05.zip
RUN mkdir -p $DATA_DIR
RUN curl http://twofishes.net/indexes/revgeo/$TWOFISHES_LATEST_PREBUILT_INDEX > $DATA_DIR/$TWOFISHES_LATEST_PREBUILT_INDEX \
  && unzip $DATA_DIR/$TWOFISHES_LATEST_PREBUILT_INDEX -d $DATA_DIR \
  && rm $DATA_DIR/$TWOFISHES_LATEST_PREBUILT_INDEX

EXPOSE 8080
# TODO(dan): It'd be nice if the prebuilt index didn't have this funky subfolder.
CMD ["./src/jvm/io/fsq/twofishes/scripts/serve.py", "-p", "8080", "/data/twofishes/2015-03-05-20-05-30.753698"]
