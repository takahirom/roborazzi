curl -L https://nightly.link/takahirom/roborazzi/workflows/test.yaml/main/screenshots.zip > original.zip
unzip -o original.zip -d original
./gradlew app:testDebugUnitTest --stacktrace
cd app/build/outputs/roborazzi
mkdir diff
find . -name "*.png" -or -name "*.gif" | xargs -IIMG compare ../../../../original/IMG IMG diff/IMG

