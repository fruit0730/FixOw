@echo off
setlocal
if "%ANDROID_JAR%"=="" goto :missing
if "%BUILD_TOOLS%"=="" goto :missing
if "%KS%"=="" goto :missing
if "%KS_PASS%"=="" goto :missing
if "%KS_ALIAS%"=="" goto :missing
if "%KS_KEY_PASS%"=="" set KS_KEY_PASS=%KS_PASS%

rmdir /s /q gen classes dexout 2>/dev/null
del /q res.zip base.apk unsigned.apk aligned.apk FixOw.apk 2>/dev/null
mkdir gen classes dexout

"%BUILD_TOOLS%\aapt2" compile --dir res -o res.zip || exit /b 1
"%BUILD_TOOLS%\aapt2" link -o base.apk --manifest AndroidManifest.xml -I "%ANDROID_JAR%" --java gen -A assets res.zip || exit /b 1
javac --release 11 -classpath "%ANDROID_JAR%" -sourcepath stubs -d classes src\cn\classfruit\fixow\Hook.java src\cn\classfruit\fixow\MainActivity.java gen\cn\classfruit\fixow\R.java || exit /b 1
"%BUILD_TOOLS%\d8" --release --lib "%ANDROID_JAR%" --output dexout classes\cn\classfruit\fixow\*.class || exit /b 1
python -c "import zipfile;zin=zipfile.ZipFile('base.apk');zout=zipfile.ZipFile('unsigned.apk','w',zipfile.ZIP_DEFLATED);[zout.writestr(i,zin.read(i.filename)) for i in zin.infolist() if i.filename!='classes.dex'];zout.write('dexout/classes.dex','classes.dex');zout.close();zin.close()" || exit /b 1
"%BUILD_TOOLS%\zipalign" -f 4 unsigned.apk aligned.apk || exit /b 1
"%BUILD_TOOLS%\apksigner" sign --ks "%KS%" --ks-pass pass:%KS_PASS% --key-pass pass:%KS_KEY_PASS% --ks-key-alias %KS_ALIAS% --out FixOw.apk aligned.apk || exit /b 1
echo built: FixOw.apk
exit /b 0

:missing
echo Set ANDROID_JAR, BUILD_TOOLS, KS, KS_PASS, KS_ALIAS (and KS_KEY_PASS) first.
exit /b 1
