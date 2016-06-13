namespace java io.fsq.thriftexample.people.gen

enum PhoneType {
  HOME
  OFFICE
  CELL
  OTHER
}

const i16 USA = 1

struct PhoneNumber {
  11: optional i16 countryCode = USA
  12: optional i32 areaCode
  13: required i64 number
  14: optional PhoneType phoneType
  99: optional string meta
  100: optional string yield
}

struct StreetAddress {
  1: required string address1
  2: optional string address2
  3: required string city
  4: optional string state
  5: optional string country
  6: optional string postcode
}

union ContactInfo {
  1: optional PhoneNumber phone
  2: optional StreetAddress address
  3: optional string email
}

enum Gender {
  MALE = 1
  FEMALE = 2
  OTHER = 3
}

struct Person {
  1: required string firstName
  2: optional string lastName
  3: required Gender gender
  4: optional list<ContactInfo> contacts
}
