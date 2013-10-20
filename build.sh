#!/bin/sh
#
#  Usage:
#
# for a list of all available build-targets type
#   ./build.sh -projecthelp 
# 
chmod u+x ./bin/antRun
chmod u+x ./bin/ant
export ANT_HOME=.
$PWD/bin/ant -logger org.apache.tools.ant.NoBannerLogger -emacs $@ 
