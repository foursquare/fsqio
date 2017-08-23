## Spindle annotations

The `SpindleAnnotations` class is for accessing thrift annotations from scala code.

### What is an annotation?

```thrift
struct CarPartsOrder {
  1: optional int orderId
  2: optional int orderValue (currency="dollars")
  3: optional list<int> productids
} (
  table_name="car_parts_orders"
)
```

* `table_name` is a struct annotation
* `currency` is a field annotation -- SpindleAnnotations doesn't make these available yet

### Example

```ini
# pants.ini

[gen.spindle]
write_annotations_json: true
```

```scala
// Whatever.scala

// This will be a Map[String, Map[String, String]].
// i.e. {classBinaryName: {key: value}}
val map = SpindleAnnotations.mergedAnnotations()
```
