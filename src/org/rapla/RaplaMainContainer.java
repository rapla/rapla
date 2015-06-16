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
package org.rapla;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
/**
The Rapla Main Container class for the basic container for Rapla specific services and the rapla plugin architecture.
The rapla container has only one instance at runtime. 
<p>
The Main Container provides the following Services to all RaplaComponents
<ul>
<li>I18nBundle</li>
<li>AppointmentFormater</li>
<li>RaplaLocale</li>
<li>LocaleSelector</li>
</ul>
</p>

  @see I18nBundle
  @see RaplaLocale
  @see AppointmentFormater
  @see LocaleSelector
  
  @deprecated use RaplaClient instead
 */
@Deprecated
public class RaplaMainContainer extends RaplaClient
{
    public RaplaMainContainer(  StartupEnvironment env) throws Exception
    {
        super(  env );
    }
 }

