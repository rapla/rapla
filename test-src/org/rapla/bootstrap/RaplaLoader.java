package org.rapla.bootstrap;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
  Puts all jar-files from the libdirs into the classpath and start the mainClass.
  Usage:
  <code>
        Syntax:  baseDir libdir1,libdir2,... mainClass arg1 arg2 ...

        Will put all jar-files from the libdirs into the classpath and start the mainClass

          baseDir: replace with the path, from wich you want the lib-dirs to resolve.
          libdir[1-n]: Lib-Directories relativ to the base jars.");
          mainClass: The Java-Class you want to start after loading the jars.
          arg[1-n]:

         Example:  ./lib common,client org.rapla.Main rapla

         loads the jars in lib/common and lib/client and
         starts org.rapla.Main with the argument rapla
  </code>
 */
public class RaplaLoader {
    /** returns all *.jar files in the directories passed in dirList relative to the baseDir */
    public static File[] getJarFilesAndClassesFolder(String baseDir,String dirList) throws IOException {
        ArrayList<File> completeList = new ArrayList<File>();
        StringTokenizer tokenizer = new StringTokenizer(dirList,",");
        while (tokenizer.hasMoreTokens())
        {
            File jarDir = new File(baseDir,tokenizer.nextToken());
            if (jarDir.exists() && jarDir.isDirectory())
            {
              File[] jarFiles = jarDir.listFiles();
              for (int i = 0; i < jarFiles.length; i++) {
                  if (
                      jarFiles[i].getAbsolutePath().endsWith(".jar")
                    )
                  {
                      completeList.add(jarFiles[i].getCanonicalFile());
                  }
              }
              completeList.add( jarDir.getCanonicalFile() );
            }
        }
        return  completeList.toArray(new File[] {});
    }

   
    private static void printUsage() {
        System.out.println("Syntax:  baseDir libdir1,libdir2,... mainClass arg1 arg2 ...");
        System.out.println();
        System.out.println("Will put all jar-files from the libdirs into the classpath and start the mainClass");
        System.out.println();
        System.out.println("  baseDir: replace with the path, from wich you want the lib-dirs to resolve.");
        System.out.println("  libdir[1-n]: Lib-Directories relativ to the base jars.");
        System.out.println("  mainClass: The Java-Class you want to start after loading the jars.");
        System.out.println("  arg[1-n]: ");
        System.out.println();
        System.out.println(" Example:  ./lib common,client org.rapla.Main rapla ");
        System.out.println("loads the jars in lib/common and lib/client and ");
        System.out.println("  starts org.rapla.Main with the argument rapla");
    }

    public static void main(String[] args) {
        String baseDir;
        String dirList;
        String classname;
        String[] applicationArgs;
       
        if (args.length <3) {
            printUsage();
            System.exit(1);
        }
        baseDir=args[0];
        dirList=args[1];
        classname=args[2];

        applicationArgs = new String[args.length-3];
        for (int i=0;i<applicationArgs.length;i++)
        {
            applicationArgs[i] = args[i+3];
        }
        start( baseDir, dirList, classname, "main",new Object[] {applicationArgs} );
    }


    public static void start( String baseDir, String dirList, String classname, String methodName,Object[] applicationArgs )
    {
        try{
            ClassLoader classLoader = getJarClassloader(baseDir, dirList);
            Thread.currentThread().setContextClassLoader(classLoader);
            startMain(classLoader,classname,methodName,applicationArgs);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
    
    


	private static ClassLoader getJarClassloader(String baseDir, String dirList)
			throws IOException, MalformedURLException {
		File[] jarFiles = getJarFilesAndClassesFolder(baseDir,dirList);
		URL[] urls = new URL[jarFiles.length];
		for (int i = 0; i < jarFiles.length; i++) {
		    urls[i] = jarFiles[i].toURI().toURL();
		    //System.out.println(urls[i]);
		}
		

		
		final ClassLoader classLoader = new URLClassLoader
		    (
		     urls
		    ,RaplaLoader.class.getClassLoader()
		    );
		return classLoader;
	}

    private static void startMain(ClassLoader classLoader,String classname, String methodName,Object[] args) throws Exception {
    	Class<?> startClass = classLoader.loadClass(classname);
        Class<?>[] argTypes = new Class[args.length];
        for ( int i=0;i<args.length;i++)
        {
        	argTypes[i] = args[i].getClass();
        }
        Method mainMethod = startClass.getMethod(methodName,argTypes);
        mainMethod.invoke(null, args);
    }
    
//    public static void startFromWar( String baseDir, String dirList,String warfile, String classname, String methodName,Object[] applicationArgs )
//    {
//        try{
//            ClassLoader loader = getJarClassloader(baseDir, dirList);
//            Thread.currentThread().setContextClassLoader(loader);
//			Class<?> forName = loader.loadClass("org.slf4j.bridge.SLF4JBridgeHandler");
//	    	forName.getMethod("removeHandlersForRootLogger", new Class[] {}).invoke(null, new Object[] {});
//	    	forName.getMethod("install", new Class[] {}).invoke(null, new Object[] {});
//            loader.loadClass("org.rapla.bootstrap.JNDIInit").newInstance();
//
//            File file = new File(baseDir, warfile);
//            ClassLoader classLoader;
//            if (file.exists())
//            {
//	            Class<?> webappContextClass = loader.loadClass("org.eclipse.jetty.webapp.WebAppContext");
//	            //org.eclipse.jetty.webapp.WebAppContext context = new org.eclipse.jetty.webapp.WebAppContext("webapps/rapla.war","/");
//	            Constructor<?> const1 = webappContextClass.getConstructor(String.class, String.class);
//				Object context  = const1.newInstance(new Object[] {file.getPath(),"/"});
//	            Class<?> webappClassLoaderClass = loader.loadClass("org.eclipse.jetty.webapp.WebAppClassLoader");
//	            Class<?> contextClass = loader.loadClass("org.eclipse.jetty.webapp.WebAppClassLoader$Context");
//				//org.eclipse.jetty.webapp.WebAppClassLoader classLoader = new org.eclipse.jetty.webapp.WebAppClassLoader(parentClassLoader, context);
//	            Constructor<?> const2 = webappClassLoaderClass.getConstructor(ClassLoader.class, contextClass);
//				classLoader = (ClassLoader)  const2.newInstance(loader, context);
//				webappContextClass.getMethod("setClassLoader", ClassLoader.class).invoke( context,classLoader);	
//				Class<?> configurationClass = loader.loadClass("org.eclipse.jetty.webapp.WebInfConfiguration");
//				Object config = configurationClass.newInstance();
//				configurationClass.getMethod("preConfigure", webappContextClass).invoke(config, context);	
//				configurationClass.getMethod("configure", webappContextClass).invoke(config, context);
//            }
//            else
//            {
//            	classLoader = loader;
//            	System.err.println("War " + warfile + " not found. Using original classpath.");
//            }
//			startMain(classLoader,classname,methodName,applicationArgs);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            System.exit(1);
//        }
//    }
    
    
}
