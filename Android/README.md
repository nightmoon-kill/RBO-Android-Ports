# Build the Android APK / คอมไพล์ APK

[Installation and controls / วิธีติดตั้งและตั้งค่าปุ่ม](../README.md)

## ภาษาไทย

สคริปต์นี้ใช้บน Linux x86-64 ติดตั้ง JDK, Bash, zip, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.3.13750724 และ [Eclipse Compiler for Java 3.38.0](https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/) จากนั้นรันคำสั่งด้านล่างจากโฟลเดอร์หลักของ repo

ไฟล์ที่ยังไม่ได้เซ็นอยู่ที่ `Android/build/aligned.apk` หากต้องการเซ็นด้วยคีย์ของตัวเอง ให้ตั้งค่า `APK_KEYSTORE`, `APK_KEY_ALIAS` และ `APK_KEYSTORE_PASSWORD`

## English

The script runs on Linux x86-64. Install a JDK, Bash, zip, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.3.13750724, and [Eclipse Compiler for Java 3.38.0](https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/). Run the following command from the repository root.

The unsigned output is `Android/build/aligned.apk`. Set `APK_KEYSTORE`, `APK_KEY_ALIAS`, and `APK_KEYSTORE_PASSWORD` to sign the APK with your own key.

```sh
ANDROID_HOME=/path/to/android-sdk ECJ_JAR=/path/to/ecj-3.38.0.jar ./Android/build.sh
```
