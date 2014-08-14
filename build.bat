@echo off
:: -------------------------------------------------------------------------
:: build.bat Skript for Rapla
::
::
:: Usage:
:: for a list of all available build-targets type
::  .\build.bat -projecthelp 
if not "%ANT_HOME%" =="" goto gotAntHome
set ANT_HOME=.
:gotAntHome
call %ANT_HOME%\bin\ant.bat %1 %2 %3 %4 %5 %6
pause
