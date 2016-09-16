// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.benchmark.test;

import io.fsq.spindle.benchmark.gen.BenchmarkExample;
import io.fsq.spindle.benchmark.gen.BenchmarkExample$;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ReadBenchmark {
    byte[] sparse = new byte[]{24, 12, 87, -36, 34, 73, -65, 28, 35, 87, 67, -113, -61, -7, 56, 11, 72, 101, 108, 108,
            111, 32, 87, 111, 114, 108, 100, 28, 24, 6, 83, 116, 114, 105, 110, 103, 21, 84, 0, 0};
    byte[] dense = new byte[]{24, 12, 87, -36, 34, 73, -65, 28, 35, 87, 67, -113, -61, -7, 21, -14, -64, 1, 22, -114,
            -38, -106, 1, 24, 11, 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 28, 24, 6, 83, 116, 114, 105, 110,
            103, 21, 84, 0, 25, 60, 24, 7, 83, 116, 114, 105, 110, 103, 49, 21, -82, 17, 0, 24, 7, 83, 116, 114, 105,
            110, 103, 50, 21, -36, 34, 0, 24, 7, 83, 116, 114, 105, 110, 103, 51, 21, -118, 52, 0, 25, -107, 2, 2, 4, 6,
            10, 16, 26, 42, 68, 24, 12, 87, -36, 34, 90, -65, 28, 35, 87, 67, -113, -61, -6, 22, -80, -75, -88, -66,
            -26, 85, 0};
    private final TDeserializer deser = new TDeserializer(new TCompactProtocol.Factory());

    @Benchmark
    //@Fork(jvmArgsAppend={"-XX:+UnlockDiagnosticVMOptions","-XX:+PrintAssembly"})
    public BenchmarkExample readSparse() throws TException {
        BenchmarkExample b = BenchmarkExample$.MODULE$.createRawRecord();
        deser.deserialize(b, sparse);
        return b;
    }

    @Benchmark
    //@Fork(jvmArgsAppend={"-XX:+UnlockDiagnosticVMOptions","-XX:+PrintAssembly"})
    public BenchmarkExample readDense() throws TException {
        BenchmarkExample b = BenchmarkExample$.MODULE$.createRawRecord();
        deser.deserialize(b, dense);
        return b;
    }
}
