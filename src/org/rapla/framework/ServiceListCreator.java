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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.rapla.framework.logger.Logger;
import org.rapla.gwt.client.Rapla;

import com.google.gwt.inject.client.GinModule;

/** Helper Class for automated creation of the rapla-plugin.list in the
 * META-INF directory. Can be used in the build environment.
 */
public class ServiceListCreator
{

    public interface SelectorStrategy
    {
        boolean isClassNamePossible(File classFile);

        boolean isClassPossible(Class<?> clazz);
    }

    private static final SelectorStrategy pluginSelectorStrategy = new SelectorStrategy()
    {

        @Override
        public boolean isClassPossible(Class<?> clazz)
        {
            return !clazz.isInterface() && PluginDescriptor.class.isAssignableFrom(clazz);
        }

        @Override
        public boolean isClassNamePossible(File classFile)
        {
            String name = classFile.getName();
            return classFile.getAbsolutePath().contains("rapla") && (name.endsWith("Plugin.class") || name.endsWith("PluginServer.class"));
        }
    };

    private static final SelectorStrategy gwtPluginSelectorStrategy = new SelectorStrategy()
    {
        @Override
        public boolean isClassPossible(Class<?> clazz)
        {
            return !clazz.isInterface() && GinModule.class.isAssignableFrom(clazz);
        }

        @Override
        public boolean isClassNamePossible(File classFile)
        {
            String name = classFile.getName();
            return classFile.getAbsolutePath().contains("rapla") && name.endsWith("Plugin.class");
        }
    };

    public static void main(String[] args)
    {
        try
        {
            String sourceDir = args[0];
            String destDir = (args.length > 1) ? args[1] : sourceDir;
            processDir(sourceDir, destDir);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e.getMessage());
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void processDir(String srcDir, String destFile) throws ClassNotFoundException, IOException
    {
        File topDir = new File(srcDir);
        System.out.println("generating " + destFile);
        createPluginList(destFile, topDir, pluginSelectorStrategy);
        final String gwtDestFile = destFile + "-gwt";
        System.out.println("generating " + gwtDestFile);
        createPluginList(gwtDestFile, topDir, gwtPluginSelectorStrategy);
        createGwtXml(topDir, gwtDestFile);
    }

    private static void createGwtXml(File topDir, String gwtDestFile) throws IOException, ClassNotFoundException
    {
        final Set<String> list = plugins(gwtDestFile);
        String destFile = topDir.getParentFile().getAbsolutePath() + File.separator + "generated-resources" + File.separator + "org" + File.separator + "rapla"
                + File.separator + "gwt" + File.separator + "Rapla.gwt.xml";
        final File file = new File(destFile);
        if (file.exists())
        {
            file.delete();
        }
        else
        {
            file.getParentFile().mkdirs();
        }
        final Writer writer = new BufferedWriter(new FileWriter(destFile));
        try
        {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.5.1//EN\"\n");
            writer.write("  \"http://google-web-toolkit.googlecode.com/svn/tags/2.5.1/distro-source/core/src/gwt-module.dtd\">\n");
            writer.write("<module rename-to='Rapla'>\n");
            writer.write("  <inherits name='com.google.gwt.xml.XML' />\n");
            writer.write("  <inherits name='com.google.gwt.user.User'/>\n");
            writer.write("  <inherits name='com.google.gwt.http.HTTP'/>\n");
            writer.write("  <inherits name='com.google.gwt.logging.Logging'/>\n");
            writer.write("  <inherits name='com.google.gwt.i18n.CldrLocales' />\n");
            writer.write("  <inherits name='com.google.gwt.i18n.I18N' />\n");
            writer.write("  <inherits name='com.google.gwt.user.theme.clean.Clean'/>\n");
            writer.write("  <inherits name='com.google.gwt.inject.Inject'/>\n");
            writer.write("  <inherits name='org.rapla.Rapla_main_module'/>\n");
            //            writer.write("  <inherits name='org.gwtbootstrap3.GwtBootstrap3Theme'/>\n");
            //            writer.write("  <inherits name='org.gwtbootstrap3.extras.datepicker.DatePicker'/>\n");
            writer.write("  <source path='client'/>\n");
            writer.write("  <entry-point class='" + Rapla.class.getCanonicalName() + "'/>\n");
            writer.write("  <set-property name='user.agent' value='safari' />\n");
            writer.write("  <extend-property name='locale' values='de' />\n");
            writer.write("  <set-property name='locale' value='de' />\n");
            writer.write("  <set-property name='gwt.logging.logLevel' value='INFO'/>\n");
            writer.write("  <set-property name='gwt.logging.enabled' value='TRUE'/>\n");
            writer.write("  <set-configuration-property name='devModeRedirectEnabled' value='true'/>\n");
            writer.write("  <define-configuration-property name='extra.ginModules' is-multi-valued='true' />\n");
            for (String className : list)
            {
                writer.write("  <extend-configuration-property name='extra.ginModules' value='");
                writer.write(className);
                writer.write("' />\n");
            }
            writer.write("</module>");
        }
        finally
        {
            writer.close();
        }
    }

    private static Set<String> plugins(String gwtDestFile) throws IOException
    {
        Set<String> pluginNames = new LinkedHashSet<String>();
        Enumeration<URL> pluginEnum = ServiceListCreator.class.getClassLoader().getResources("META-INF/rapla-plugin.list-gwt");
        while (pluginEnum.hasMoreElements())
        {
            final InputStreamReader is = new InputStreamReader((pluginEnum.nextElement()).openStream());
            readPlugins(pluginNames, is);
        }
        readPlugins(pluginNames, new InputStreamReader(new FileInputStream(new File(gwtDestFile))));
        return pluginNames;
    }

    private static void readPlugins(Set<String> pluginNames, final InputStreamReader is) throws IOException
    {
        BufferedReader reader = new BufferedReader(is);
        while (true)
        {
            String plugin = reader.readLine();
            if (plugin == null)
                break;
            pluginNames.add(plugin);
        }
    }

    private static void createPluginList(String destFile, File topDir, SelectorStrategy selectorStrategy) throws ClassNotFoundException, IOException
    {
        List<String> list = findPluginClasses(topDir, null, selectorStrategy);
        final File file = new File(destFile);
        if (file.exists())
        {
            file.delete();
        }
        else
        {
            file.getParentFile().mkdirs();
        }
        Writer writer = new BufferedWriter(new FileWriter(destFile));
        try
        {
            for (String className : list)
            {
                System.out.println("Found PluginDescriptor for " + className);
                writer.write(className);
                writer.write("\n");
            }
        }
        finally
        {
            writer.close();
        }
    }

    public static List<String> findPluginClasses(File topDir, Logger logger, SelectorStrategy selectorStrategy) throws ClassNotFoundException
    {
        List<String> list = new ArrayList<String>();
        Stack<File> stack = new Stack<File>();
        stack.push(topDir);
        while (!stack.empty())
        {
            File file = stack.pop();
            if (file.isDirectory())
            {
                File[] files = file.listFiles();
                for (int i = 0; i < files.length; i++)
                    stack.push(files[i]);
            }
            else
            {
                if (selectorStrategy.isClassNamePossible(file))
                {
                    String absolut = file.getAbsolutePath();
                    String relativePath = absolut.substring(topDir.getAbsolutePath().length());
                    String pathName = relativePath.substring(1, relativePath.length() - ".class".length());
                    String className = pathName.replace(File.separatorChar, '.');
                    try
                    {
                        Class<?> pluginClass = ServiceListCreator.class.getClassLoader().loadClass(className);
                        if (selectorStrategy.isClassPossible(pluginClass))
                        {
                            list.add(className);
                        }
                        else
                        {
                            if (logger != null)
                            {
                                logger.warn("No PluginDescriptor found for Class " + className);
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
    public static Collection<String> findPluginClasses(Logger logger) throws ClassNotFoundException
    {
        Collection<String> result = new LinkedHashSet<String>();
        URL mainDir = ServiceListCreator.class.getResource("/");
        if (mainDir != null)
        {
            String classpath = System.getProperty("java.class.path");
            final String[] split;
            if (classpath != null)
            {
                split = classpath.split("" + File.pathSeparatorChar);
            }
            else
            {
                split = new String[] { mainDir.toExternalForm() };
            }
            for (String path : split)
            {
                File pluginPath = new File(path);
                List<String> foundInClasspathEntry = findPluginClasses(pluginPath, logger, pluginSelectorStrategy);
                result.addAll(foundInClasspathEntry);
            }
        }
        return result;
    }

    /** lookup for plugin classes in classpath*/
    public static Collection<File> findPluginWebappfolders(Logger logger) throws ClassNotFoundException
    {
        Collection<File> result = new LinkedHashSet<File>();
        URL mainDir = ServiceListCreator.class.getResource("/");
        if (mainDir != null)
        {
            String classpath = System.getProperty("java.class.path");
            final String[] split;
            if (classpath != null)
            {
                split = classpath.split("" + File.pathSeparatorChar);
            }
            else
            {
                split = new String[] { mainDir.toExternalForm() };
            }
            FilenameFilter filter = new FilenameFilter()
            {
                @Override
                public boolean accept(File dir, String name)
                {
                    return name != null && name.equals("war");
                }
            };
            for (String path : split)
            {
                File pluginPath = new File(path);
                List<String> foundInClasspathEntry = findPluginClasses(pluginPath, logger, pluginSelectorStrategy);

                if (foundInClasspathEntry.size() > 0)
                {
                    File parent = pluginPath.getParentFile().getAbsoluteFile();
                    int depth = 0;
                    while (parent != null && parent.isDirectory())
                    {
                        File[] listFiles = parent.listFiles(filter);
                        if (listFiles != null && listFiles.length == 1)
                        {
                            result.add(listFiles[0]);
                        }
                        depth++;
                        if (depth > 5)
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
