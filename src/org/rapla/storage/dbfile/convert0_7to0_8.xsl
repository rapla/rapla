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

 <xsl:variable name="highest_resource_id">
    <xsl:for-each select="/rapla:data/rapla:resources/rapla:resource">
      <xsl:sort select="substring-after(@id,'resource_')" data-type="number" order="descending"/>
      <xsl:if test="position() = 1">
        <xsl:value-of select="substring-after(@id,'resource_')"/>
      </xsl:if>
    </xsl:for-each>
 </xsl:variable>

<xsl:template match="rapla:data">
<rapla:data version="0.8"
            xmlns:rapla="http://rapla.sourceforge.net/rapla"
            xmlns:relax="http://relaxng.org/ns/structure/1.0"
            xmlns:dynatt="http://rapla.sourceforge.net/dynamictype"
            xmlns:doc="http://rapla.sourceforge.net/annotation"
>
 <xsl:apply-templates/> 
</rapla:data>
</xsl:template>

<xsl:template match="rapla:person[@id]">
  <xsl:variable name="lastid" select="substring-after(@id,'person_')"/>
  <xsl:variable name="id" select="$highest_resource_id + $lastid"/>
  <rapla:person id="resource_{$id}">
      <xsl:apply-templates select="*"/>
  </rapla:person>
</xsl:template>

<xsl:template match="rapla:person[@idref]">
  <xsl:variable name="lastid" select="substring-after(@idref,'person_')"/>
  <xsl:variable name="id" select="$highest_resource_id + $lastid"/>
  <rapla:person idref="resource_{$id}">
      <xsl:apply-templates select="*"/>
  </rapla:person>
</xsl:template>

<xsl:template match="rapla:allocate[contains(@idref, 'person')]">
  <xsl:variable name="lastid" select="substring-after(@idref,'person_')"/>
  <xsl:variable name="id" select="$highest_resource_id + $lastid"/>
  <rapla:allocate idref="resource_{$id}">
      <xsl:apply-templates select="*"/>
  </rapla:allocate>
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


<xsl:template match="@*|*|text()|processing-instruction()"><xsl:copy><xsl:apply-templates select="@*|*|text()|processing-instruction()"/></xsl:copy></xsl:template>

</xsl:stylesheet>
