package org.rapla.server.internal;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.rapla.framework.ServiceListCreator;
import org.rapla.framework.logger.Logger;

public class JettyDevelopment
{
	// add the war folders of the plugins to jetty resource handler so that the files inside the war 
	// folders can be served from within jetty, even when they are not located in the same folder.
	// The method will search the class path for plugin classes and then add the look for a war folder entry in the file hierarchy
	// so a plugin allways needs a plugin class for this to work
	@SuppressWarnings("unchecked")
	static public void addDevelopmentWarFolders(Logger logger)
	{
		  Thread currentThread = Thread.currentThread();
		  ClassLoader classLoader = currentThread.getContextClassLoader();
		  ClassLoader parent = null;
		  try
		  {
			  Collection<File> webappFolders = ServiceListCreator.findPluginWebappfolders(logger);
			  if ( webappFolders.size() < 1)
			  {
				  return;
			  }
			  parent = classLoader.getParent();
			  if ( parent != null)
			  {
				  currentThread.setContextClassLoader( parent);
			  }

			  // first we need to access the necessary classes via reflection (are all loaded, because webapplication is already initialized) 
			  final Class WebAppClassLoaderC = Class.forName("org.eclipse.jetty.webapp.WebAppClassLoader",false, parent);
			  final Class WebAppContextC = Class.forName("org.eclipse.jetty.webapp.WebAppContext",false, parent);
			  final Class ResourceCollectionC = Class.forName("org.eclipse.jetty.util.resource.ResourceCollection",false, parent);
			  final Class FileResourceC = Class.forName("org.eclipse.jetty.util.resource.FileResource",false, parent);
			  final Object webappContext = WebAppClassLoaderC.getMethod("getContext").invoke(classLoader);
			  if  (webappContext == null)
			  {
				  return;
			  }
			  final Object baseResource = WebAppContextC.getMethod("getBaseResource").invoke( webappContext);
			  if ( baseResource != null && ResourceCollectionC.isInstance( baseResource) )
			  {
				  
				  //Resource[] resources = ((ResourceCollection) baseResource).getResources();
				  final Object[] resources = (Object[])ResourceCollectionC.getMethod("getResources").invoke( baseResource);
				  Set list = new HashSet( Arrays.asList( resources));
				  for (File folder:webappFolders)
				  {
					  Object fileResource = FileResourceC.getConstructor( URL.class).newInstance(  folder.toURI().toURL());
					  if ( !list.contains( fileResource))
					  {
						  list.add( fileResource);
						  logger.info("Adding " + fileResource + " to webapp folder");
					  }
					  
				  }
				  Object[] array = list.toArray( resources);
				  //((ResourceCollection) baseResource).setResources( array);
				  ResourceCollectionC.getMethod("setResources", resources.getClass()).invoke( baseResource, new Object[] {array});
				  //ResourceCollectionC.getMethod(", parameterTypes)  
			  }
		  }
		  catch (ClassNotFoundException ex)
		  {
			  logger.info("Development mode not in jetty so war finder will be disabled");
		  }
		  catch (Exception ex)
		  {
			  logger.error(ex.getMessage(), ex);
		  }
		  finally
		  {
			  if ( parent != null)
			  {
				  currentThread.setContextClassLoader( classLoader);
			  }
		  }
	}
}