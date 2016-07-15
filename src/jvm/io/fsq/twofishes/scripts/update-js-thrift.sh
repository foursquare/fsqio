#!/bin/sh

thrift --gen js -o server/src/main/resources/twofishes-static/ interface/src/main/thrift/geocoder.thrift
