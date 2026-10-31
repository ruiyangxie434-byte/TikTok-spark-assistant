package app.local.spark.background;
final class NodeEngine {
    static { System.loadLibrary("c++_shared"); System.loadLibrary("node"); System.loadLibrary("spark_node"); }
    static native int start(String script);
}
