// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.benchmark.test;

import io.fsq.spindle.benchmark.gen.BenchmarkExample;
import io.fsq.spindle.benchmark.gen.BenchmarkExample$;
import io.fsq.spindle.common.thrift.serde.ThriftReusingSerializer;
import io.fsq.spindle.runtime.structs.gen.InnerStruct;
import io.fsq.spindle.runtime.structs.gen.InnerStruct$;
import org.apache.thrift.TException;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import java.util.Arrays;
import scala.Tuple2;
import scala.collection.immutable.Vector$;
import scala.collection.mutable.WrappedArray;

@State(Scope.Benchmark)
public class WriteBenchmark {
    BenchmarkExample sparse = BenchmarkExample$.MODULE$.newBuilder()
            .id(new ObjectId("57dc2249bf1c2357438fc3f9"))
            .message("Hello World")
            .singleStruct(InnerStruct$.MODULE$.newBuilder().aString("String").anInt(42).result())
            .result();
    BenchmarkExample dense = BenchmarkExample$.MODULE$.newBuilder()
            .id(new ObjectId("57dc2249bf1c2357438fc3f9"))
            .intField(12345)
            .longField(1234567L)
            .message("Hello World")
            .singleStruct(InnerStruct$.MODULE$.newBuilder().aString("String").anInt(42).result())
            .structList(Vector$.MODULE$.apply(WrappedArray.make(new InnerStruct[]{
                    InnerStruct$.MODULE$.newBuilder().aString("String1").anInt(1111).result(),
                    InnerStruct$.MODULE$.newBuilder().aString("String2").anInt(2222).result(),
                    InnerStruct$.MODULE$.newBuilder().aString("String3").anInt(3333).result()
            })))
            .intList(Vector$.MODULE$.apply(WrappedArray.make(new int[]{1, 1, 2, 3, 5, 8, 13, 21, 34})))
            .id2(new ObjectId("57dc225abf1c2357438fc3fa"))
            .dt(new DateTime(1474044431704L))
            .result();
    private final ThriftReusingSerializer ser = new ThriftReusingSerializer();

    //Copy the resulting array to keep the benchmark similar to what TSerializer does.
    @Benchmark
    //@Fork(jvmArgsAppend={"-XX:+UnlockDiagnosticVMOptions","-XX:+PrintAssembly"})
    public byte[] writeSparse() throws TException {
        Tuple2 t = ser.serialize(sparse);
        return Arrays.copyOf((byte[])t._1, (Integer)t._2);
    }

    @Benchmark
    //@Fork(jvmArgsAppend={"-XX:+UnlockDiagnosticVMOptions","-XX:+PrintAssembly"})
    public byte[] writeDense() throws TException {
        Tuple2 t = ser.serialize(dense);
        return Arrays.copyOf((byte[])t._1, (Integer)t._2);
    }
}
