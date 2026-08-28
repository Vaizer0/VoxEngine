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

# commons-compress is used to extract model .tar.bz2 archives on-device; keep the
# writer/reader service-loader entries that are looked up reflectively.
-keep class org.apache.commons.compress.** { *; }

