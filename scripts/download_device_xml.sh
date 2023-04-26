curl "https://android.googlesource.com/platform/tools/base/+archive/mirror-goog-studio-master-dev/sdklib/src/main/java/com/android/sdklib/devices.tar.gz" > devices.tar.gz
mkdir devices
tar -xzf devices.tar.gz -C devices
rm devices.tar.gz
