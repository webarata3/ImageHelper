SET JAVA_HOME=C:\first\dev\jdk-21.0.2
SET PATH=%JAVA_HOME%/bin;%PATH%;C:\first\dev\bin\wix314-binaries
jar cfe ImageHelper.jar app.webarata3.imagehelper.App -C bin/main .

@REM jlink --module-path "%JAVA_HOME%/jmods" --add-modules java.base,java.desktop --output custom-runtime

jpackage ^
  --input . ^
  --name ImageHelper ^
  --main-jar ImageHelper.jar ^
  --main-class app.webarata3.imagehelper.App ^
  --icon res/icon.ico ^
  --runtime-image custom-runtime ^
  --type exe ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --app-version 1.0 ^
  --vendor "ARATA Shinichi" ^
  --dest .
