This README is for building rapla from source. You will find more information and the rapla sources on 

http://rapla.org

We recommend reading the faqs and the development documents under https://github.com/rapla/rapla/wiki for more information.
especially the https://github.com/rapla/rapla/wiki/BuildGuide

We use the ant tool for our build process. Ant is the java-equivalent to make
Look at jakarta.apache.org/ant for more information. If you don't want 
to use Ant read the FAQS and developers doc  for alternative building.


Use the following batch files to start the ant.jar included with 
the rapla source distribution.

build.sh (Linux/Unix) 
build.bat  (Win) 

Calling build with no arguments will create a binary-distribution
in the sub-folder dist. There you will find the scripts to start rapla. (SEE INSTALL.txt)


You can call build with different arguments, e.g.:

 choose-target Executes the target specified in build.properties. 
   (default)   Default target is dist-bin.

 dist-bin      Creates a new Binary-Distribution in the folder dist.
 build         Builds a new rapla.war in the build sub-folder
 clean         Deletes the build sub-folder
 clean-dist    Deletes the dist sub-folder
 javadocs      create the Javadoc in the build sub-folder
 
Pass the target as a parameter to ant
e.g. build.sh dist-bin
				   
For a complete list of all targets use the -projecthelp option
