# kotlinx.serialization、Room、OkHttp 都自带混淆规则，这里只放额外需要的。

# 保留行号，崩溃时便于定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# sherpa-onnx 的原生代码按名字读取这些类的字段，不能混淆
-keep class com.k2fsa.sherpa.onnx.** { *; }
