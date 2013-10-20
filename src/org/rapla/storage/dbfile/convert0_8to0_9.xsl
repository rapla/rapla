<?xml version="1.0" encoding="utf-8"?><!--*- coding: utf-8 -*-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:rapla="http://rapla.sourceforge.net/rapla"
                  xmlns:doc="http://rapla.sourceforge.net/annotation"
                  xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
                  xmlns:relax="http://relaxng.org/ns/structure/1.0"
>
<!-- this script set increases the ids of person objects, so that the
     int values of the ids are unique not only to person but also to
     resource objects.
--> 

<xsl:output indent="yes"/>
<xsl:output encoding="utf-8"/>

<xsl:template match="rapla:data">
<rapla:data version="0.9"
            xmlns:rapla="http://rapla.sourceforge.net/rapla"
            xmlns:relax="http://relaxng.org/ns/structure/1.0"
            xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
            xmlns:doc="http://rapla.sourceforge.net/annotation"
>
 <xsl:apply-templates/> 
</rapla:data>
</xsl:template>

<xsl:template match="relax:grammar">
  <xsl:copy-of select="."/> 
  <rapla:preferences>
   <rapla:entry key="org.rapla.plugin">
	<rapla:config>
      <pluginlist>
		<plugin enabled="true" class="org.rapla.plugin.periodwizard.PeriodWizardPlugin"/>
		<plugin enabled="true" class="org.rapla.plugin.autoexport.AutoExportPlugin"/>
	  </pluginlist>
	</rapla:config>
   </rapla:entry>
  </rapla:preferences>
</xsl:template>

 <xsl:template match="rapla:filters">
 </xsl:template>

 <xsl:template match="rapla:config[@role='org.rapla.plugin.default-reservationfilter']">
 </xsl:template>

 <xsl:template match="rapla:config[@role='org.rapla.plugin.default-allocatablefilter']">
 </xsl:template>

 <xsl:template match="rapla:config[@role='org.rapla.plugin.notification.config']">
    <rapla:entry key="org.rapla.plugin.notification.notify_if_owner" value="{notify-if-owner}"/>
 </xsl:template>


 <xsl:template match="rapla:config[@role='org.rapla.plugin.notification.allocationlisteners']">
   <rapla:entry key="org.rapla.plugin.notification.allocationlisteners">
	 <rapla:map>
	   <xsl:for-each select="rapla:resource">
		 <rapla:mapentry key="{position()}">
		   <xsl:variable name="id" select="@idref"/>
           <xsl:if test="/rapla:data/rapla:resources/rapla:resource[@id = $id]">
	  	     <rapla:resource idref="{$id}"/>
		   </xsl:if>
		 </rapla:mapentry>
	   </xsl:for-each>
	 </rapla:map>
   </rapla:entry>
 </xsl:template>

 <xsl:template match="rapla:config[@role='org.rapla.plugin.autoexport']">
   <rapla:entry key="org.rapla.plugin.autoexport">
    <rapla:map>
	 <xsl:for-each select="export">
	  <rapla:mapentry key="{@filename}">
	   <xsl:for-each select="weekview">
		 <xsl:variable name="view">
		   <xsl:choose>
			 <xsl:when test="string-length(@view)>0">
			   <xsl:value-of select="@view"/>
			 </xsl:when>
			 <xsl:otherwise>
			   <xsl:value-of select="'week'"/>
			 </xsl:otherwise>
		   </xsl:choose>
		 </xsl:variable>
		 <xsl:variable name="type">
		   <xsl:choose>
			 <xsl:when test="rapla:reservationfilter">
			   <xsl:value-of select="'reservation'"/>
			 </xsl:when>
			 <xsl:otherwise>
			   <xsl:value-of select="'resource'"/>
			 </xsl:otherwise>
		   </xsl:choose>
		 </xsl:variable>
		 <rapla:calendar date="{@start-date}" view="{$view}" title="{@title}" rapla-type="{$type}">
		   <filter>
			 <xsl:variable name="id" select="rapla:allocatablefilter/@idref"/>
			 <xsl:apply-templates select="/rapla:data/rapla:filters/rapla:allocatablefilter[@id=$id]"/>
			 <xsl:variable name="id1" select="rapla:reservationfilter/@idref"/>
			 <xsl:apply-templates select="/rapla:data/rapla:filters/rapla:reservationfilter[@id=$id1]"/>
		   </filter>
           <selected>
			 <rapla:map>
			   <xsl:for-each select="selected/rapla:resource">
				 <xsl:variable name="id" select="@idref"/>
				 <xsl:if test="/rapla:data/rapla:resources/rapla:resource[@id = $id]">
				   <rapla:mapentry key="{position()}">
					 <rapla:resource idref="{$id}"/>
				   </rapla:mapentry>
				 </xsl:if>
			   </xsl:for-each>
 			   <xsl:for-each select="selected/rapla:reservation">
				 <xsl:variable name="id" select="@idref"/>
				 <xsl:if test="/rapla:data/rapla:reservations/rapla:reservation[@id = $id]">
				   <rapla:mapentry key="{position()}">
					 <rapla:reservation idref="{id}"/>
				   </rapla:mapentry>
				 </xsl:if>
			   </xsl:for-each>
 			   <xsl:for-each select="selected/rapla:dynamictype">
				 <xsl:variable name="keyref" select="@keyref"/>
				 <xsl:if test="/rapla:data/relax:grammar/relax:define[@name = $keyref]">
				   <rapla:mapentry key="{position()}">
					 <rapla:dynamictype keyref="{$keyref}"/>
				   </rapla:mapentry>
				 </xsl:if>
			   </xsl:for-each>
			 </rapla:map>
		   </selected>
		 </rapla:calendar>
	   </xsl:for-each>
      </rapla:mapentry>
	 </xsl:for-each>
    </rapla:map>
   </rapla:entry>
 </xsl:template>

 <xsl:template match="rapla:user[@id]">
   <rapla:user id="{@id}" username="{@username}" password="{@password}" name="{@name}" email="{@email}" isAdmin="{boolean(rapla:role[@name='admin'])}">
	 <xsl:if test="boolean(rapla:role[@name='registerer']) ">
	   <rapla:group key="category[key='registerer']"/>
	 </xsl:if>
	 <rapla:group key="category[key='modify-preferences']"/>
	 <xsl:apply-templates select="*"/>
   </rapla:user>
 </xsl:template>

 <xsl:template match="rapla:user[@idref]">
   <rapla:user idref="{@idref}"/>
 </xsl:template>

 <xsl:template match="rapla:category[@key='user-groups']">
   <rapla:category key="user-groups">
	 <xsl:apply-templates/>
	 <xsl:if test="not(rapla:category[@key='registerer'])">
	    <rapla:category key="registerer">
		  <doc:name lang="en">register resources</doc:name>
		  <doc:name lang="de">Ressourcen eintragen</doc:name>
		  <doc:name lang="fr">Enregistrer des ressources</doc:name>
		</rapla:category>
	 </xsl:if>
	 <xsl:if test="not(rapla:category[@key='modify-preferences'])">
	    <rapla:category key="modify-preferences">
		  <doc:name lang="en">modify preferences</doc:name>
		  <doc:name lang="de">Einstellungen bearbeiten</doc:name>
		  <doc:name lang="fr">Modifier la préférence</doc:name>
		</rapla:category>
	 </xsl:if>
   </rapla:category>
 </xsl:template>

 <xsl:template match="@*|*|text()|processing-instruction()"><xsl:copy><xsl:apply-templates select="@*|*|text()|processing-instruction()"/></xsl:copy></xsl:template>

</xsl:stylesheet>
