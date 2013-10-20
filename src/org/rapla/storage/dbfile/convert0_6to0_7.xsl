<?xml version="1.0" encoding="utf-8"?><!--*- coding: utf-8 -*-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:rapla="http://rapla.sourceforge.net/rapla"
                  xmlns:doc="http://rapla.sourceforge.net/annotation"
                  xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
                  xmlns:relax="http://relaxng.org/ns/structure/1.0"
>

<xsl:output indent="yes"/>
<xsl:output encoding="utf-8"/>
<xsl:template match="rapla:data">
<rapla:data version="0.7"
            xmlns="http://rapla.sourceforge.net/configuration"
            xmlns:rapla="http://rapla.sourceforge.net/rapla"
            xmlns:relax="http://relaxng.org/ns/structure/1.0"
            xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
            xmlns:doc="http://rapla.sourceforge.net/annotation"
>
 <xsl:apply-templates/> 
</rapla:data>
</xsl:template>

<xsl:template match="relax:element[@rapla:type]">
 <relax:element name="{@name}" >
    <xsl:copy-of select="doc:name"/>
    <doc:annotations>
      <xsl:copy-of select="doc:annotations/rapla:annotation"/>
      <rapla:annotation key="classification-type"><xsl:value-of select="@rapla:type"/></rapla:annotation>
    </doc:annotations>
    <xsl:apply-templates select="relax:element|relax:optional"/>
 </relax:element>
</xsl:template>

<xsl:template match="relax:element[@rapla:category]">
 <relax:element name="{@name}" >
   <xsl:copy-of select="doc:name"/>
   <rapla:constraint name="root-category"><xsl:value-of select="@rapla:category"/></rapla:constraint>
   <relax:data type="rapla:category"/>
 </relax:element>
</xsl:template>

<xsl:template match="relax:element[@rapla:categoryidref]">
 <relax:element name="{@name}">
   <xsl:copy-of select="doc:name"/>
   <rapla:constraint name="root-category"><xsl:value-of select="@rapla:categoryidref"/></rapla:constraint>
   <relax:data type="rapla:category"/>
 </relax:element>
</xsl:template>

<xsl:template match="relax:element">
 <relax:element name="{@name}" >
   <xsl:copy-of select="*"/>
 </relax:element>
</xsl:template>


<xsl:template match="rapla:categories">
 <rapla:categories>
   <xsl:copy-of select="doc:*"/>
   <xsl:copy-of select="rapla:category"/>
   <xsl:if test="count(rapla:category[@key='user-groups']) = 0">
     <rapla:category key="user-groups">
        <doc:name lang="de">Benutzergruppen</doc:name>
        <doc:name lang="en">user-groups</doc:name>
     </rapla:category>
   </xsl:if>
 </rapla:categories>
</xsl:template>

<xsl:template match="rapla:reservations">
  <rapla:periods>
      <xsl:apply-templates select="rapla:period"/>
  </rapla:periods>
  <rapla:reservations>
      <xsl:apply-templates select="rapla:reservation"/>
  </rapla:reservations>
</xsl:template>

<xsl:template match="@*|*|text()|processing-instruction()"><xsl:copy><xsl:apply-templates select="@*|*|text()|processing-instruction()"/></xsl:copy></xsl:template>

</xsl:stylesheet>
