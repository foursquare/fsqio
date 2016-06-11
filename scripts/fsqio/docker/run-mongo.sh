#! /bin/sh

mongod --dbpath $1 &
shift
exec "$@"
