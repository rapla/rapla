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
<rapla:data version="1.0"
            xmlns:rapla="http://rapla.sourceforge.net/rapla"
            xmlns:relax="http://relaxng.org/ns/structure/1.0"
            xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
            xmlns:doc="http://rapla.sourceforge.net/annotation"
>
 <xsl:apply-templates/> 
</rapla:data>
</xsl:template>

<xsl:template match="rapla:appointment">
  <rapla:appointment start-date="{@start-date}" start-time="{@start-time}" end-date="{@end-date}" end-time="{@end-time}">
  <xsl:apply-templates/>
  <xsl:variable name="id" select="@id"/>
  <xsl:for-each select="../rapla:allocate/rapla:appointment[@idref=$id]">
     <rapla:allocate idref="{../@idref}"/>
  </xsl:for-each>
  </rapla:appointment>
</xsl:template>

<xsl:template match="rapla:allocate[@idref]/rapla:appointment">
</xsl:template>

<xsl:template match="@*|*|text()|processing-instruction()"><xsl:copy><xsl:apply-templates select="@*|*|text()|processing-instruction()"/></xsl:copy></xsl:template>

</xsl:stylesheet>
