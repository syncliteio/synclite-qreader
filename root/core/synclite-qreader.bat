@echo off

set "JVM_ARGS="
if exist "%~dp0\synclite-qreader-variables.bat" (
  call "%~dp0\synclite-qreader-variables.bat"
)

if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
     set "JAVA_CMD=%JAVA_HOME%\bin\java
  ) else (
     set "JAVA_CMD=java"
  )
) else (
  set "JAVA_CMD=java"
)

"%JAVA_CMD%" %JVM_ARGS% -classpath "%~dp0\synclite-qreader.jar;%~dp0\*" com.synclite.qreader.Main %1 %2 %3 %4 %5
