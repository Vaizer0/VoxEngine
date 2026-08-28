# Keep MiMo Gson request/response models stable under R8. Gson reflects class
# structure and generic signatures when parsing API responses.
# 模型集中在 mimo/model 子包，引擎逻辑代码（MiMoEngine/MiMoTTSClient）仍受 R8 优化。
-keep class com.voxengine.engine.mimo.model.** { *; }
-keepattributes Signature,*Annotation*

# RoleProfile / RoleVoiceStyle are reflected by Gson for the reader role-config
# round-trip in DataStore. R8 must not rename the class (the generic signature
# Map<String,RoleVoiceStyle> references it by original name) nor its fields.
-keep class com.voxengine.reader.RoleProfile { *; }
-keep class com.voxengine.reader.RoleVoiceStyle { *; }

# VoiceEntity is reflected by Gson during voice config import/export.
-keep class com.voxengine.data.VoiceEntity { *; }

# sherpa-onnx: native JNI entry points must survive R8 (methods are called from .so via JNI).
-keep class com.k2fsa.sherpa.onnx.** { *; }

# commons-compress is used to extract model .tar.bz2 archives on-device. Keep only
# the tar + bzip2 classes we reference; ignore R8's missing-class warnings for the
# optional codecs (zstd/brotli/xz/lzma/asm/pack200) that aren't on the classpath.
-keep class org.apache.commons.compress.archivers.tar.** { *; }
-keep class org.apache.commons.compress.compressors.bzip2.** { *; }
-keep class org.apache.commons.compress.utils.IOUtils { *; }
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.apache.commons.compress.compressors.xz.**
-dontwarn org.apache.commons.compress.compressors.lzma.**
-dontwarn org.apache.commons.compress.compressors.zstandard.**
-dontwarn org.apache.commons.compress.compressors.brotli.**
-dontwarn org.apache.commons.compress.harmony.**

