namespace java io.fsq.twofishes.gen

include "io/fsq/twofishes/geocoder.thrift"

enum GeocoderLogReason {
  NoResults = 1
  Exception = 2
  NoFineGrainedResults = 3
}

struct GeocoderException {
  1: optional string exceptionType
  2: optional string message
  3: optional string extraInfo
}

struct GeocoderLog {
  1: optional string method
  2: optional string className
  3: optional geocoder.GeocodeRequest request
  4: optional GeocoderLogReason reason
  5: optional GeocoderException exceptionDetails
}
