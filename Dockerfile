FROM java:openjdk-8

RUN apt-get update && apt-get install -y libpq-dev build-essential python-dev

ENV BUILD_DIR /app/fsqio
RUN mkdir -p $BUILD_DIR
COPY . $BUILD_DIR

WORKDIR $BUILD_DIR
ENTRYPOINT ["./pants", "pom-resolve"]
