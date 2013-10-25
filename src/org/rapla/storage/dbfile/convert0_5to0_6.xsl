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
<rapla:data version="0.6"
            xmlns="http://rapla.sourceforge.net/configuration"
            xmlns:rapla="http://rapla.sourceforge.net/rapla"
            xmlns:relax="http://relaxng.org/ns/structure/1.0"
            xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
            xmlns:doc="http://rapla.sourceforge.net/annotation"
>
 <xsl:apply-templates/> 
</rapla:data>
</xsl:template>

<xsl:template match="rapla:allocation">
 <xsl:for-each select="rapla:resource|rapla:person">
  <rapla:allocate idref="{@idref}">
  <xsl:for-each select="../rapla:allocate">
   <xsl:apply-templates select="*"/>
  </xsl:for-each>
  </rapla:allocate>
 </xsl:for-each>
</xsl:template>

<xsl:template match="rapla:permission/@create-conflicts[.='false']">
   <xsl:attribute name="access">allocate</xsl:attribute>
</xsl:template>

<xsl:template match="@*|*|text()|processing-instruction()"><xsl:copy><xsl:apply-templates select="@*|*|text()|processing-instruction()"/></xsl:copy></xsl:template>

</xsl:stylesheet>