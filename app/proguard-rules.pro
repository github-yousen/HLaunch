# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留Room数据库实体类
-keep class com.HLaunch.data.entity.** { *; }

# 保留Gson序列化类
-keep class com.HLaunch.update.AppUpdateManager$* { *; }

# JGit
-dontwarn org.eclipse.jgit.**
-keep class org.eclipse.jgit.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-dontwarn androidx.compose.**