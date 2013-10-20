<?xml version="1.0" encoding="utf-8"?><!--*- coding: utf-8 -*-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:rapla="http://rapla.sourceforge.net/rapla"
			      xmlns:doc="http://rapla.sourceforge.net/annotation"
			      xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
			      xmlns:relax="http://relaxng.org/ns/structure/1.0">
<xsl:output indent="yes"/>
<xsl:output encoding="utf-8"/>

<xsl:template match="rapla:data/@version">
   <xsl:attribute name="version">0.5</xsl:attribute>
</xsl:template>

<xsl:template match="relax:grammar">
  <relax:grammar>
    <relax:define name="defaultResource">
      <xsl:if test="../rapla:resources/rapla:resource[not(dynatt:*)] or not(relax:define/relax:element[rapla:type='resource'])">
	 <relax:element name="defaultResource" rapla:type="resource">
	   <doc:name lang="en">default resource</doc:name>
	   <doc:name lang="de">Standard Ressource</doc:name>
           <doc:annotations>
              <rapla:annotation key="nameformat">{name}</rapla:annotation>
           </doc:annotations>
	   <relax:element name="name">
	     <doc:name lang="en">name</doc:name>
	     <doc:name lang="de">Name</doc:name>
	     <relax:data type="string"/>
	   </relax:element>
	 </relax:element> 
      </xsl:if>
      <xsl:if test="../rapla:resources/rapla:person[not(dynatt:*)] or not(relax:define/relax:element[rapla:type='person'])">
	 <relax:element name="defaultPerson" rapla:type="person">
	   <doc:name lang="en">person</doc:name>
	   <doc:name lang="de">Person</doc:name>
           <doc:annotations>
              <rapla:annotation key="nameformat">{surname} {forename}</rapla:annotation>
           </doc:annotations>
	   <xsl:if test="../rapla:resources/rapla:person[not(dynatt:*)]">
  	     <relax:element name="title">
	       <doc:name lang="en">title</doc:name>
	       <doc:name lang="de">Titel</doc:name>
	       <relax:data type="string"/>
	     </relax:element>
           </xsl:if>
	   <relax:element name="surname">
	     <doc:name lang="en">surname</doc:name>
	     <doc:name lang="de">Nachname</doc:name>
	     <relax:data type="string"/>
	   </relax:element>
	   <relax:element name="forename">
	     <doc:name lang="en">forename</doc:name>
	     <doc:name lang="de">Vorname</doc:name>
	     <relax:data type="string"/>
	   </relax:element>
	 </relax:element> 
      </xsl:if>
      <xsl:if test="../rapla:reservations/rapla:reservation[not(dynatt:*)] or not(relax:define/relax:element[rapla:type='reservation'])">
	<relax:element name="defaultReservation" rapla:type="reservation">
 	   <doc:name lang="en">default reservation</doc:name>
	   <doc:name lang="de">Standard Reservierung</doc:name>
           <doc:annotations>
              <rapla:annotation key="nameformat">{name}</rapla:annotation>
           </doc:annotations>
	   <relax:element name="name">
	     <doc:name lang="en">eventname</doc:name>
	     <doc:name lang="de">Veranstaltungsname</doc:name>
	     <relax:data type="string"/>
	   </relax:element>
	</relax:element> 
      </xsl:if>
    </relax:define>
    <xsl:apply-templates select="relax:define[relax:element/@rapla:type='resource']" mode="addName"/>
    <xsl:apply-templates select="relax:define[relax:element/@rapla:type='person']" mode="addName"/>
    <xsl:apply-templates select="relax:define[relax:element/@rapla:type='reservation']" mode="addName"/>
  </relax:grammar>
</xsl:template>

<xsl:template match="relax:define" mode="addName">
  <relax:define name="{@name}">
    <xsl:apply-templates select="relax:element" mode="addName"/>
  </relax:define>
</xsl:template>

<xsl:template match="allocate">
   <rapla:allocate>
      <xsl:apply-templates/>
   </rapla:allocate>
</xsl:template>

<xsl:template match="rapla:resources">
   <rapla:users>
     <xsl:apply-templates select="rapla:user"/>
   </rapla:users>

   <rapla:resources>
     <xsl:apply-templates select="rapla:resource|rapla:person"/>
   </rapla:resources>
</xsl:template>

<xsl:template match="relax:element" mode="addName">
   <relax:element name="{@name}" rapla:type="{@rapla:type}">
      <xsl:apply-templates select="doc:name"/>
      <xsl:choose>
        <xsl:when test="@rapla:type='person'">
          <doc:annotations>
             <rapla:annotation key="nameformat">{surname} {forename}</rapla:annotation>
          </doc:annotations>
          <relax:element name="title">
            <doc:name lang="en">title</doc:name>
            <doc:name lang="de">Titel</doc:name>
            <relax:data type="string"/>
          </relax:element>
          <relax:element name="surname">
            <doc:name lang="en">surname</doc:name>
            <doc:name lang="de">Nachname</doc:name>
            <relax:data type="string"/>
          </relax:element>
          <relax:element name="forename">
            <doc:name lang="en">forename</doc:name>
            <doc:name lang="de">Vorname</doc:name>
            <relax:data type="string"/>
          </relax:element>
          <xsl:apply-templates select="relax:element[not(@name='title') and not(@name='surname') and not(@name='forename')]|relax:optional"/>
        </xsl:when>
        <xsl:otherwise>
          <doc:annotations>
             <rapla:annotation key="nameformat">{name}</rapla:annotation>
          </doc:annotations>
          <relax:element name="name">
            <doc:name lang="en">name</doc:name>
            <doc:name lang="de">Name</doc:name>
            <relax:data type="string"/>
          </relax:element>
          <xsl:apply-templates select="relax:element[not(@name='name')]|relax:optional"/>
        </xsl:otherwise>
      </xsl:choose>
   </relax:element>
</xsl:template>

<xsl:template match="rapla:resource[@id]">
        <rapla:resource id="{@id}">
   <xsl:choose>
     <xsl:when test="dynatt:*">   
        <xsl:apply-templates select="dynatt:*" mode="addName"/>
     </xsl:when>   
     <xsl:otherwise>   
           <dynatt:defaultResource>
              <dynatt:name><xsl:value-of select="@name"/></dynatt:name>
           </dynatt:defaultResource>
     </xsl:otherwise>   
   </xsl:choose>
	</rapla:resource>
</xsl:template>

<xsl:template match="rapla:person[@id]">
        <rapla:person id="{@id}">
   <xsl:choose>
     <xsl:when test="dynatt:*">   
        <xsl:apply-templates select="dynatt:*" mode="addPersonName"/>
     </xsl:when>   
     <xsl:otherwise>   
           <dynatt:defaultPerson>
              <dynatt:title><xsl:value-of select="@title"/></dynatt:title>
              <dynatt:surname><xsl:value-of select="@surname"/></dynatt:surname>
              <dynatt:forename><xsl:value-of select="@forename"/></dynatt:forename>
           </dynatt:defaultPerson>
     </xsl:otherwise>   
   </xsl:choose>
	</rapla:person>
</xsl:template>

<xsl:template match="rapla:reservation">
        <rapla:reservation id="{@id}" owner="{rapla:user/@idref}">
   <xsl:choose>
     <xsl:when test="dynatt:*">   
        <xsl:apply-templates select="dynatt:*" mode="addName"/>
     </xsl:when>   
     <xsl:otherwise>   
           <dynatt:defaultReservation>
              <dynatt:name><xsl:value-of select="@name"/></dynatt:name>
           </dynatt:defaultReservation>
     </xsl:otherwise>   
   </xsl:choose>
	   <xsl:apply-templates select="rapla:appointment|rapla:allocation"/>
	</rapla:reservation>
</xsl:template>

<xsl:template match="dynatt:*" mode="addName">
  <xsl:element name="dynatt:{local-name()}">
    <dynatt:name><xsl:value-of select="../@name"/></dynatt:name>
    <xsl:apply-templates select="dynatt:*" mode="addAttributes"/>
  </xsl:element>
</xsl:template>

<xsl:template match="dynatt:*" mode="addPersonName">
  <xsl:element name="dynatt:{local-name()}">
    <dynatt:title><xsl:value-of select="../@title"/></dynatt:title>
    <dynatt:surname><xsl:value-of select="../@surname"/></dynatt:surname>
    <dynatt:forename><xsl:value-of select="../@forename"/></dynatt:forename>
    <xsl:apply-templates select="dynatt:*" mode="addPersonAttributes"/>
  </xsl:element>
</xsl:template>

<xsl:template match="dynatt:*" mode="addAttributes">
  <xsl:if test="not(local-name()='name') and not(@select)">
    <xsl:copy-of select="."/>
  </xsl:if>
  <xsl:if test="@select">
    <xsl:element name="dynatt:{local-name()}">
      <xsl:value-of select="@select"/>
    </xsl:element>
  </xsl:if>
</xsl:template>

<xsl:template match="dynatt:*" mode="addPersonAttributes">
  <xsl:if test="not(local-name()='title') and not(local-name()='surname') and not(local-name()='forename') and not(@select)">
    <xsl:copy-of select="."/>
  </xsl:if>
  <xsl:if test="@select">
    <xsl:element name="dynatt:{local-name()}">
      <xsl:value-of select="@select"/>
    </xsl:element>
  </xsl:if>
</xsl:template>

<xsl:template match="@freq">
   <xsl:attribute name="interval"><xsl:value-of select="."/></xsl:attribute>
</xsl:template>

<xsl:template match="@*|*|text()|processing-instruction()"><xsl:copy><xsl:apply-templates select="@*|*|text()|processing-instruction()"/></xsl:copy></xsl:template>

</xsl:stylesheet>