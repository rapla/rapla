#!/bin/sh
# 
# Script for starting @doc.name@ version @doc.version@  with jetty webserver under Unix
# Set either JAVA_HOME to point at your Java Development Kit installation.
# or PATH to point at the java command
usage()
{
    echo "Usage: $0 {run|start|stop|restart|supervise} "
    echo "run     : starts rapla-server and wait for Control-c "
    echo "start   : starts rapla-server in the background (use only when run works)"
    echo "stop    : stops rapla-server in the background "
    echo "restart : calls stop then start "
    echo "import  : imports the data from xml-file into the database "
    echo "export  : exports the data from the database into the xml-file"
    exit 1
}

##################################################
# Find directory function
##################################################
findDirectory()
{
    OP=$1
    shift
    for L in $* ; do
        [ $OP $L ] || continue 
        echo $L
        break
    done 
}


# ----- Verify and Set Required Environment Variables -------------------------

PRG=$0
ACTION=$1


while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
PRGDIR=`dirname "$PRG"`

if [ -z "$JAVA_HOME" ] ;  then
  JAVA=`which java`
  if [ -z "$JAVA" ] ; then
    echo "Cannot find JAVA. You must set JAVA_HOME to point at your Java Development Kit installation."
    exit 1
  fi
  JAVA_BINDIR=`dirname $JAVA`
  JAVA_HOME=$JAVA_BINDIR/..
  echo "Guessing JAVA_HOME:" $JAVA_HOME
fi
if [ -z "$JAVA_OPTIONS" ] ;  then
  JAVA_OPTIONS="-Xmx512M"
  echo "Guessing JAVA_OPTIONS:" $JAVA_OPTIONS
fi

JAVA="$JAVA_HOME/bin/java"
echo "PROGDIR" $PRGDIR
cd $PRGDIR
RUN_CMD="$JAVA $JAVA_OPTIONS -cp raplabootstrap.jar -Djava.awt.headless=true org.rapla.bootstrap.RaplaServerLoader"

#####################################################
# Find a location for the pid file
#####################################################
if [  -z "$JETTY_RUN" ] 
then
  JETTY_RUN=`findDirectory -w /var/run /usr/var/run .`
fi

#####################################################
# Find a PID for the pid file
#####################################################
if [  -z "$JETTY_PID" ] 
then
  JETTY_PID="$JETTY_RUN/raplajetty.pid"
fi

#####################################################
# Find a location for the jetty console
#####################################################
if [  -z "$JETTY_CONSOLE" ] 
then
  if [ -w /dev/console ]
  then
    JETTY_CONSOLE=/dev/console
  else
    JETTY_CONSOLE=/dev/tty
  fi
fi


# ----- Do the action ----------------------------------------------------------

##################################################
# Do the action
##################################################
case "$ACTION" in
  import)
  	    $PRGDIR/rapla.sh import
  	    ;;
  export)
  	    $PRGDIR/rapla.sh export
  	    ;;
  start)
        if [ -f $JETTY_PID ]
        then
            echo "WARNING $JETTY_PID found. Jetty is probably running!!"
        fi
        echo "Running Rapla in Jetty: " $RUN_CMD
       	exec $RUN_CMD >>$JETTY_CONSOLE 2>&1 1>/dev/null &
        echo $! > $JETTY_PID
        echo "Jetty running pid="`cat $JETTY_PID`
        ;;
  stop)
        PID=`cat $JETTY_PID 2>/dev/null`
        echo "Shutting down Jetty: $PID"
        kill $PID 2>/dev/null
        sleep 2
        kill -9 $PID 2>/dev/null
        rm -f $JETTY_PID
        echo "STOPPED `date`" >>$JETTY_CONSOLE
        ;;

  restart)
        $0 stop $*
        sleep 5
        $0 start $*
        ;;

  supervise)
       #
       # Under control of daemontools supervise monitor which
       # handles restarts and shutdowns via the svc program.
       #
         exec $RUN_CMD
         ;;

  run)
        echo "Running Rapla in Jetty: " $RUN_CMD
        exec $RUN_CMD
        ;;

*)
        usage
	;;
esac

exit 0
