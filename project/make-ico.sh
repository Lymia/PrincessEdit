#!/bin/sh
rm -rv ico
mkdir -v ico
cp -rv icon-*.svg ico
for size in 16 20 22 24 32 40 48 64 96 256; do 
  inkscape -e ico/icon-app-$size.png -z -w $size -h $size icon-app.svg
done
for size in 16 20 22 24 32 40 48 64 96 256; do 
  inkscape -e ico/icon-document-$size.png -z -w $size -h $size icon-document.svg
done
convert -verbose ico/icon-app-{16,20,22,24,32,40,48,64,96,256}.png ico/icon-app.ico
convert -verbose ico/icon-document-{16,20,22,24,32,40,48,64,96,256}.png ico/icon-document.ico
rm -v ico/*.png
