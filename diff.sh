curl -L https://nightly.link/takahirom/roborazzi/workflows/test.yaml/main/screenshots.zip > original.zip
unzip -o original.zip -d original
rm -R app/build/outputs/roborazzi
./gradlew app:testDebugUnitTest --stacktrace --rerun
cd app/build/outputs/roborazzi
mkdir diff
find . -name "*.png" | xargs -IIMG compare ../../../../original/IMG IMG diff/IMG
for IMG in `find . -name "*.gif"`; do
  magick \( ../../../../original/$IMG -coalesce -append \) \
            \( $IMG -coalesce -append \) miff:- | \
      magick compare - miff:- |\
        magick - +repage diff/$IMG.png
done
open diff

