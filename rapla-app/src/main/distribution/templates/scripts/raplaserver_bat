@echo off
:: -------------------------------------------------------------------------
:: startserver.bat    
:: Script for starting rapla server under Windows
:: Set either JAVA_HOME to point at your Java Development Kit installation.
:: or PATH to point at the java command
::
set RAPLA_JAVA_OPTIONS=%JAVA_OPTIONS%
if not "%JAVA_OPTIONS%" == "" goto gotJavaOptions
   set RAPLA_JAVA_OPTIONS="-Xmx512M"
:gotJavaOptions

:: Backward compatibility for old versions
if not "%1" == "import" goto conti1
  call rapla.bat import
goto finish
:conti1
if not "%1" == "export" goto conti2
  call rapla.bat export
goto finish
:conti2
call rapla.bat server %1 %2 %3 %4
:finish

