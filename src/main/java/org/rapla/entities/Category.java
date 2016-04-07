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
package org.rapla.entities;
import java.util.Locale;

import org.rapla.entities.storage.ReferenceInfo;

/** Hierarchical categorization of information.
 * Categories can be used as attribute values.
 *   @see org.rapla.entities.dynamictype.Attribute
 */
public interface Category extends MultiLanguageNamed,Entity<Category>,Timestamp, Annotatable, Comparable
{
    ReferenceInfo<Category> SUPER_CATEGORY_REF = new ReferenceInfo<Category>("category_0", Category.class);
    /** add a sub-category.
     * This category is set as parent of the passed category.*/
    void addCategory(Category category);
    /** remove a sub-category */
    void removeCategory(Category category);
    /** returns all subcategories */
    Category[] getCategories();
    /** returns the subcategory with the specified key.
     * null if subcategory was not found. */
    Category getCategory(String key);
    /** Returns the parent of this category or null if the category has no parent.*/
    Category getParent();
    /** returns true if the passed category is a direct child of this category */
    boolean hasCategory(Category category);
    /** set the key of the category. The can be used in the getCategory() method for lookupDeprecated. */
    void setKey(String key);
    /** returns the key of the category */
    String getKey();
    /** returns true this category is an ancestor
     *  (parent or parent of parent, ...) of the specified
     * category */
    boolean isAncestorOf(Category category);
    /** returns the path form the rootCategory to this category.
     *   Path elements are the category-names in the selected locale separated
     *   with the / operator. If the rootCategory is null the path will be calculated
     *   to the top-most parent.
     *   Example: <strong>area51/aliencell</strong>
    */
    String getPath(Category rootCategory,Locale locale);

    /** returns the max depth of the cildrens  */
    int getDepth();
   
    /** returns the number of ancestors.
     * (How many Time you must call getParent() until you receive null) */
    int getRootPathLength();
    
    Category[] CATEGORY_ARRAY = new Category[0];

    Iterable<Category> getCategoryList();
}
