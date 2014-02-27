package org.rapla.gui;

import java.awt.Component;

import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;

public interface EditController
{
    <T extends Entity> EditComponent<T> createUI( T obj ) throws RaplaException;

    <T extends Entity> void edit( T obj, Component owner ) throws RaplaException;
    <T extends Entity> void editNew( T obj, Component owner ) throws RaplaException;
    <T extends Entity> void edit( T obj, String title, Component owner ) throws RaplaException;
    
//  neue Methoden zur Bearbeitung von mehreren gleichartigen Elementen (Entities-Array)
//  orientieren sich an den oberen beiden Methoden zur Bearbeitung von einem Element
    <T extends Entity> void edit( T[] obj, Component owner ) throws RaplaException;
    <T extends Entity> void edit( T[] obj, String title, Component owner ) throws RaplaException;


}