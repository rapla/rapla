/*--------------------------------------------------------------------------*
| Copyright (C) 2014 Christopher Kohlhaas                                  |
|                                                                          |
| This program is free software; you can redistribute it and/or modify     |
| it under the terms of the GNU General Public License as published by the |
| Free Software Foundation. A copy of the license has been included with   |
| these distribution in the COPYING file, if not go to www.fsf.org         |
|                                                                          |
| As a special exception, you are granted the permissions to link this     |
| program with every library, which license fulfills the Open Source       |
| Definition as published by the Open Source Initiative (OSI).             |
*--------------------------------------------------------------------------*/
package org.rapla.framework;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Stack;

import org.rapla.framework.logger.Logger;



/** Helper Class for automated creation of the rapla-plugin.list in the
 * META-INF directory. Can be used in the build environment.
 */
public class ServiceListCreator {
    public static void main (String[] args) {
        try {
            String sourceDir = args[0];
            String destDir = (args.length>1) ? args[1] : sourceDir;
            processDir(sourceDir,destDir);
        } catch (IOException e) {
            throw new RuntimeException( e.getMessage());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException( e.getMessage());
        }
    }

    public static void processDir(String srcDir,String destFile)
    throws ClassNotFoundException, IOException
    {
        File topDir = new File(srcDir);
    	List<String> list = findPluginClasses(topDir, null);
        Writer writer = new BufferedWriter(new FileWriter( destFile ));
        try
        {
        	for ( String className:list)
        	{
        		System.out.println("Found PluginDescriptor for " + className);
        		writer.write( className  );
        		writer.write( "\n" );
        	}
        } finally {
            writer.close();
        }
    }

	public static List<String> findPluginClasses(File topDir,Logger logger)
			throws ClassNotFoundException {
		List<String> list = new ArrayList<String>();
    	Stack<File> stack = new Stack<File>();
        stack.push(topDir);
        while (!stack.empty()) {
            File file = stack.pop();
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (int i=0;i<files.length;i++)
                    stack.push(files[i]);
            } else {
                String name = file.getName();
				if (file.getAbsolutePath().contains("rapla") &&  (name.endsWith("Plugin.class") || name.endsWith("PluginServer.class"))) {
                    String absolut = file.getAbsolutePath();
                    String relativePath = absolut.substring(topDir.getAbsolutePath().length());
                    String pathName = relativePath.substring(1,relativePath.length()-".class".length());
                    String className = pathName.replace(File.separatorChar,'.');
                    try
                    {
	                    Class<?> pluginClass = ServiceListCreator.class.getClassLoader().loadClass(className );
	                    if (!pluginClass.isInterface() ) {
	                        if (  PluginDescriptor.class.isAssignableFrom(pluginClass)) {
	                        	list.add( className);
	                           
	                        } else {
	                        	if ( logger != null)
	                        	{
	                        		logger.warn("No PluginDescriptor found for Class " + className );
	                        	}
	                        }
	                    }
                    }
                    catch (NoClassDefFoundError ex)
                    {
                    	 System.out.println(ex.getMessage());
                    }
                }
            }
        }
		return list;
	}

	/** lookup for plugin classes in classpath*/
	public static Collection<String> findPluginClasses(Logger logger)
			throws ClassNotFoundException {
		Collection<String> result = new LinkedHashSet<String>();
		URL mainDir = ServiceListCreator.class.getResource("/");
		if ( mainDir != null)
		{
			String classpath = System.getProperty("java.class.path");
			final String[] split;
			if (classpath != null)
			{
				split = classpath.split(""+File.pathSeparatorChar);
			}
			else
			{
				split = new String[] { mainDir.toExternalForm()};
			}
			for ( String path: split)
			{
				File pluginPath = new File(path);
				List<String> foundInClasspathEntry = findPluginClasses(pluginPath, logger);
				result.addAll(foundInClasspathEntry);
			}
		}
		return result;
	}
	
	/** lookup for plugin classes in classpath*/
	public static Collection<File> findPluginWebappfolders(Logger logger)
			throws ClassNotFoundException {
		Collection<File> result = new LinkedHashSet<File>();
		URL mainDir = ServiceListCreator.class.getResource("/");
		if ( mainDir != null)
		{
			String classpath = System.getProperty("java.class.path");
			final String[] split;
			if (classpath != null)
			{
				split = classpath.split(""+File.pathSeparatorChar);
			}
			else
			{
				split = new String[] { mainDir.toExternalForm()};
			}
			FilenameFilter filter = new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name != null && name.equals("war");
				}
			};
			for ( String path: split)
			{
				File pluginPath = new File(path);
				List<String> foundInClasspathEntry = findPluginClasses(pluginPath, logger);
				
				if ( foundInClasspathEntry.size() > 0)
				{
					File parent = pluginPath.getParentFile().getAbsoluteFile();
					int depth= 0;
					while ( parent != null && parent.isDirectory())
					{
						File[] listFiles = parent.listFiles( filter);
						if (listFiles != null && listFiles.length == 1)
						{
							result.add( listFiles[0]);
						}
						depth ++;
						if ( depth > 5)
						{
							break;
						}
						parent = parent.getParentFile();
					}
				}
			}
		}
		return result;
	}
}
