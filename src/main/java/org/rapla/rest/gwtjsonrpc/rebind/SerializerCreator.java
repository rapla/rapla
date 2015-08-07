// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.rapla.rest.gwtjsonrpc.rebind;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.rapla.rest.gwtjsonrpc.client.impl.JsonSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.EnumSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.JavaLangString_JsonSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.JavaUtilDate_JsonSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.ListSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.ObjectArraySerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.ObjectMapSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.ObjectSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.PrimitiveArraySerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.SetSerializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.StringMapSerializer;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JPackage;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;

class SerializerCreator {
  private static final String SER_SUFFIX = "_JsonSerializer";
  private static final Comparator<JField> FIELD_COMP =
      new Comparator<JField>() {
        @Override
        public int compare(final JField o1, final JField o2) {
          return o1.getName().compareTo(o2.getName());
        }
      };

  private static final HashMap<String, String> defaultSerializers;
  private static final HashMap<String, String> parameterizedSerializers;
  static {
    defaultSerializers = new HashMap<String, String>();
    parameterizedSerializers = new HashMap<String, String>();

    defaultSerializers.put(java.lang.String.class.getCanonicalName(),
        JavaLangString_JsonSerializer.class.getCanonicalName());
    defaultSerializers.put(java.util.Date.class.getCanonicalName(),
        JavaUtilDate_JsonSerializer.class.getCanonicalName());
//    defaultSerializers.put(java.sql.Date.class.getCanonicalName(),
//        JavaSqlDate_JsonSerializer.class.getCanonicalName());
//    defaultSerializers.put(java.sql.Timestamp.class.getCanonicalName(),
//        JavaSqlTimestamp_JsonSerializer.class.getCanonicalName());
    parameterizedSerializers.put(java.util.List.class.getCanonicalName(),
        ListSerializer.class.getCanonicalName());
    parameterizedSerializers.put(java.util.Map.class.getCanonicalName(),
        ObjectMapSerializer.class.getCanonicalName());
    parameterizedSerializers.put(java.util.Set.class.getCanonicalName(),
        SetSerializer.class.getCanonicalName());
  }

  private final HashMap<String, String> generatedSerializers;
  private final GeneratorContext context;
  private JClassType targetType;

  SerializerCreator(final GeneratorContext c) {
    context = c;
    generatedSerializers = new HashMap<String, String>();
  }

  String create(final JClassType targetType, final TreeLogger logger)
      throws UnableToCompleteException {
    if (targetType.isParameterized() != null || targetType.isArray() != null) {
      ensureSerializersForTypeParameters(logger, targetType);
    }
    String sClassName = serializerFor(targetType);
    if (sClassName != null) {
      return sClassName;
    }

    checkCanSerialize(logger, targetType, true);
    recursivelyCreateSerializers(logger, targetType);

    this.targetType = targetType;
    final SourceWriter srcWriter = getSourceWriter(logger, context);
    final String sn = getSerializerQualifiedName(targetType);
    if (!generatedSerializers.containsKey(targetType.getQualifiedSourceName())) {
      generatedSerializers.put(targetType.getQualifiedSourceName(), sn);
    }
    if (srcWriter == null) {
      return sn;
    }

    if (!targetType.isAbstract()) {
      generateSingleton(srcWriter);
    }
    if (targetType.isEnum() != null) {
      generateEnumFromJson(srcWriter);
    } else {
      generateInstanceMembers(srcWriter);
      generatePrintJson(srcWriter);
      generateFromJson(srcWriter);
      generateGetSets(srcWriter);
    }

    srcWriter.commit(logger);
    return sn;
  }

  private void recursivelyCreateSerializers(final TreeLogger logger,
      final JType targetType) throws UnableToCompleteException {
    if (targetType.isPrimitive() != null || isBoxedPrimitive(targetType)) {
      return;
    }

    final JClassType targetClass = targetType.isClass();
    if (needsSuperSerializer(targetClass)) {
      create(targetClass.getSuperclass(), logger);
    }

    for (final JField f : sortFields(targetClass)) {
      ensureSerializer(logger, f.getType());
    }
  }

  Set<JClassType> createdType = new HashSet<JClassType>();
  
  private void ensureSerializer(final TreeLogger logger, final JType type)
      throws UnableToCompleteException {
    if (ensureSerializersForTypeParameters(logger, type)) {
      return;
    }

    final String qsn = type.getQualifiedSourceName();
    if (defaultSerializers.containsKey(qsn)
        || parameterizedSerializers.containsKey(qsn)) {
      return;
    }

    JClassType type2 = (JClassType) type;
	if ( createdType.contains( type2))
	{
		return;
	}
	createdType.add( type2 );
    create(type2, logger);
  }

  private boolean ensureSerializersForTypeParameters(final TreeLogger logger,
      final JType type) throws UnableToCompleteException {
    if (isJsonPrimitive(type) || isBoxedPrimitive(type)) {
      return true;
    }

    if (type.isArray() != null) {
      ensureSerializer(logger, type.isArray().getComponentType());
      return true;
    }

    if (type.isParameterized() != null) {
      for (final JClassType t : type.isParameterized().getTypeArgs()) {
        ensureSerializer(logger, t);
      }
    }

    return false;
  }

  void checkCanSerialize(final TreeLogger logger, final JType type)
      throws UnableToCompleteException {
    checkCanSerialize(logger, type, false);
  }

  Set<JClassType> checkedType = new HashSet<JClassType>();
  void checkCanSerialize(final TreeLogger logger, final JType type,
      boolean allowAbstractType) throws UnableToCompleteException {
    if (type.isPrimitive() == JPrimitiveType.LONG) {
      logger.log(TreeLogger.ERROR,
          "Type 'long' not supported in JSON encoding", null);
      throw new UnableToCompleteException();
    }
 
//    if (type.isPrimitive() == JPrimitiveType.VOID) {
//      logger.log(TreeLogger.ERROR,
//          "Type 'void' not supported in JSON encoding", null);
//      throw new UnableToCompleteException();
//    }

    final String qsn = type.getQualifiedSourceName();
    if (type.isEnum() != null) {
      return;
    }

    if (isJsonPrimitive(type) || isBoxedPrimitive(type)) {
      return;
    }

    if (type.isArray() != null) {
      final JType leafType = type.isArray().getLeafType();
      if (leafType.isPrimitive() != null || isBoxedPrimitive(leafType)) {
        if (type.isArray().getRank() != 1) {
          logger.log(TreeLogger.ERROR, "gwtjsonrpc does not support "
              + "(de)serializing of multi-dimensional arrays of primitves");
          // To work around this, we would need to generate serializers for
          // them, this can be considered a todo
          throw new UnableToCompleteException();
        } else
          // Rank 1 arrays work fine.
          return;
      }
      checkCanSerialize(logger, type.isArray().getComponentType());
      return;
    }

    if (defaultSerializers.containsKey(qsn)) {
      return;
    }

    if (type.isParameterized() != null) {
      final JClassType[] typeArgs = type.isParameterized().getTypeArgs();
      for (final JClassType t : typeArgs) {
        checkCanSerialize(logger, t);
      }
      if (parameterizedSerializers.containsKey(qsn)) {
        return;
      }
    } else if (parameterizedSerializers.containsKey(qsn)) {
      logger.log(TreeLogger.ERROR,
          "Type " + qsn + " requires type paramter(s)", null);
      throw new UnableToCompleteException();
    }

    if (qsn.startsWith("java.") || qsn.startsWith("javax.")) {
      logger.log(TreeLogger.ERROR, "Standard type " + qsn
          + " not supported in JSON encoding", null);
      throw new UnableToCompleteException();
    }

    if (type.isInterface() != null ) {
      logger.log(TreeLogger.ERROR, "Interface " + qsn
          + " not supported in JSON encoding", null);
      throw new UnableToCompleteException();
    }

    final JClassType ct = (JClassType) type;
    if ( checkedType.contains( ct))
    {
    	return;
    }
    checkedType.add( ct );
    if (ct.isAbstract() && !allowAbstractType) {
      logger.log(TreeLogger.ERROR, "Abstract type " + qsn
          + " not supported here", null);
      throw new UnableToCompleteException();
    }
    for (final JField f : sortFields(ct)) {
      final TreeLogger branch =
          logger.branch(TreeLogger.DEBUG, "In type " + qsn + ", field "
              + f.getName());
      checkCanSerialize(branch, f.getType());
    }
  }

  String serializerFor(final JType t) {
    if (t.isArray() != null) {
      final JType componentType = t.isArray().getComponentType();
      if (componentType.isPrimitive() != null
          || isBoxedPrimitive(componentType))
        return PrimitiveArraySerializer.class.getCanonicalName();
      else
        return ObjectArraySerializer.class.getCanonicalName() + "<"
            + componentType.getQualifiedSourceName() + ">";
    }

    if (isStringMap(t)) {
      return StringMapSerializer.class.getName();
    }

    final String qsn = t.getQualifiedSourceName();
    if (defaultSerializers.containsKey(qsn)) {
      return defaultSerializers.get(qsn);
    }

    if (parameterizedSerializers.containsKey(qsn)) {
      return parameterizedSerializers.get(qsn);
    }

    return generatedSerializers.get(qsn);
  }

  private boolean isStringMap(final JType t) {
    return t.isParameterized() != null
        && t.getErasedType().isClassOrInterface() != null
        && t.isParameterized().getTypeArgs().length > 0
        && t.isParameterized().getTypeArgs()[0].getQualifiedSourceName()
            .equals(String.class.getName())
        && t.getErasedType().isClassOrInterface().isAssignableTo(
            context.getTypeOracle().findType(Map.class.getName()));
  }

  private void generateSingleton(final SourceWriter w) {
      w.print("public static final ");
      w.print("javax.inject.Provider<"+getSerializerSimpleName()+">");
      
      w.print(" INSTANCE_PROVIDER = new javax.inject.Provider<");
      w.print(getSerializerSimpleName());
      w.println(">(){");
      w.print("public " + getSerializerSimpleName() + " get(){return INSTANCE;} " );
      w.println("};");
      w.println();
      
      w.print("public static final ");
    w.print(getSerializerSimpleName());
    w.print(" INSTANCE = new ");
    w.print(getSerializerSimpleName());
    w.println("();");
    w.println();
  }

  private void generateInstanceMembers(final SourceWriter w) {
    for (final JField f : sortFields(targetType)) {
      final JType ft = f.getType();
      if (needsTypeParameter(ft)) {
        final String serType = serializerFor(ft);
        w.print("private final ");
        w.print(serType);
        w.print(" ");
        w.print("ser_" + f.getName());
        w.print(" = ");
        boolean useProviders= true;
        generateSerializerReference(ft, w, useProviders);
        w.println(";");
      }
    }
    w.println();
  }

  void generateSerializerReference(final JType type, final SourceWriter w, boolean useProviders) {
    String serializerFor = serializerFor(type);
	if (type.isArray() != null) {
      final JType componentType = type.isArray().getComponentType();
      if (componentType.isPrimitive() != null
          || isBoxedPrimitive(componentType)) {
        w.print(PrimitiveArraySerializer.class.getCanonicalName());
        w.print(".INSTANCE");
      } else {
        w.print("new " + serializerFor + "(");
        generateSerializerReference(componentType, w, useProviders);
        w.print(")");
      }

    } else if (needsTypeParameter(type)) {
      w.print("new " + serializerFor + "(");
      final JClassType[] typeArgs = type.isParameterized().getTypeArgs();
      int n = 0;
      if (isStringMap(type)) {
        n++;
      }
      boolean first = true;
      for (; n < typeArgs.length; n++) {
        if (first) {
          first = false;
        } else {
          w.print(", ");
        }
        generateSerializerReference(typeArgs[n], w, useProviders);
      }
      w.print(")");

    } else {
//      String sourceName = type.getQualifiedSourceName();
        
      w.print(serializerFor + ".INSTANCE" + (useProviders ? "_PROVIDER":""));
    }
  }

  private void generateGetSets(final SourceWriter w) {
    for (final JField f : sortFields(targetType)) {
      if (f.isPrivate()) {
        w.print("private static final native ");
        w.print(f.getType().getQualifiedSourceName());
        w.print(" objectGet_" + f.getName());
        w.print("(");
        w.print(targetType.getQualifiedSourceName() + " instance");
        w.print(")");
        w.println("/*-{ ");
        w.indent();

        w.print("return instance.@");
        w.print(targetType.getQualifiedSourceName());
        w.print("::");
        w.print(f.getName());
        w.println(";");

        w.outdent();
        w.println("}-*/;");

        w.print("private static final native void ");
        w.print(" objectSet_" + f.getName());
        w.print("(");
        w.print(targetType.getQualifiedSourceName() + " instance, ");
        w.print(f.getType().getQualifiedSourceName() + " value");
        w.print(")");
        w.println("/*-{ ");
        w.indent();

        w.print("instance.@");
        w.print(targetType.getQualifiedSourceName());
        w.print("::");
        w.print(f.getName());
        w.println(" = value;");

        w.outdent();
        w.println("}-*/;");
      }

      if (f.getType() == JPrimitiveType.CHAR || isBoxedCharacter(f.getType())) {
        w.print("private static final native String");
        w.print(" jsonGet0_" + f.getName());
        w.print("(final JavaScriptObject instance)");
        w.println("/*-{ ");
        w.indent();
        w.print("return instance.");
        w.print(f.getName());
        w.println(";");
        w.outdent();
        w.println("}-*/;");

        w.print("private static final ");
        w.print(f.getType() == JPrimitiveType.CHAR ? "char" : "Character");
        w.print(" jsonGet_" + f.getName());
        w.print("(JavaScriptObject instance)");
        w.println(" {");
        w.indent();
        w.print("return ");
        w.print(JsonSerializer.class.getName());
        w.print(".toChar(");
        w.print("jsonGet0_" + f.getName());
        w.print("(instance)");
        w.println(");");
        w.outdent();
        w.println("}");
      } else {
        w.print("private static final native ");
        if (f.getType().isArray() != null) {
          w.print("JavaScriptObject");
        } else if (isJsonPrimitive(f.getType())) {
          w.print(f.getType().getQualifiedSourceName());
        } else if (isBoxedPrimitive(f.getType())) {
          w.print(boxedTypeToPrimitiveTypeName(f.getType()));
        } else {
          w.print("Object");
        }
        w.print(" jsonGet_" + f.getName());
        w.print("(JavaScriptObject instance)");
        w.println("/*-{ ");
        w.indent();

        w.print("return instance.");
        w.print(f.getName());
        w.println(";");

        w.outdent();
        w.println("}-*/;");
      }

      w.println();
    }
  }

  private void generateEnumFromJson(final SourceWriter w) {
    w.print("public ");
    w.print(targetType.getQualifiedSourceName());
    w.println(" fromJson(Object in) {");
    w.indent();
    w.print("return in != null");
    w.print(" ? " + targetType.getQualifiedSourceName()
        + ".valueOf((String)in)");
    w.print(" : null");
    w.println(";");
    w.outdent();
    w.println("}");
    w.println();
  }

  private void generatePrintJson(final SourceWriter w) {
    final JField[] fieldList = sortFields(targetType);
    w.print("protected int printJsonImpl(int fieldCount, StringBuilder sb, ");
    w.println("Object instance) {");
    w.indent();

    w.print("final ");
    w.print(targetType.getQualifiedSourceName());
    w.print(" src = (");
    w.print(targetType.getQualifiedSourceName());
    w.println(")instance;");

    if (needsSuperSerializer(targetType)) {
      w.print("fieldCount = super.printJsonImpl(fieldCount, sb, (");
      w.print(targetType.getSuperclass().getQualifiedSourceName());
      w.println(")src);");
    }

    final String docomma = "if (fieldCount++ > 0) sb.append(\",\");";
    for (final JField f : fieldList) {
      final String doget;
      if (f.isPrivate()) {
        doget = "objectGet_" + f.getName() + "(src)";
      } else {
        doget = "src." + f.getName();
      }

      final String doname = "sb.append(\"\\\"" + f.getName() + "\\\":\");";
      if (f.getType() == JPrimitiveType.CHAR || isBoxedCharacter(f.getType())) {
        w.println(docomma);
        w.println(doname);
        w.println("sb.append(\"\\\"\");");
        w.println("sb.append(" + JsonSerializer.class.getSimpleName()
            + ".escapeChar(" + doget + "));");
        w.println("sb.append(\"\\\"\");");
      } else if (isJsonString(f.getType())) {
        w.println("if (" + doget + " != null) {");
        w.indent();
        w.println(docomma);
        w.println(doname);
        w.println("sb.append(" + JsonSerializer.class.getSimpleName()
            + ".escapeString(" + doget + "));");
        w.outdent();
        w.println("}");
        w.println();
      } else if (f.getType().isPrimitive() != null) {
        w.println(docomma);
        w.println(doname);
        w.println("sb.append(" + doget + ");");
        w.println();
      } else if (isJsonPrimitive(f.getType()) || isBoxedPrimitive(f.getType())) {
          w.println("if (" + doget + " != null) {");
          w.println(docomma);
          w.println(doname);
          w.println("sb.append(" + doget + ");");
          w.println();
          w.println("}");
      } else {
        w.println("if (" + doget + " != null) {");
        w.indent();
        w.println(docomma);
        w.println(doname);
        if (needsTypeParameter(f.getType())) {
          w.print("ser_" + f.getName());
        } else {
          w.print(serializerFor(f.getType()) + ".INSTANCE");
        }
        w.println(".printJson(sb, " + doget + ");");
        w.outdent();
        w.println("}");
        w.println();
      }
    }

    w.println("return fieldCount;");
    w.outdent();
    w.println("}");
    w.println();
  }

  private void generateFromJson(final SourceWriter w) {
    w.print("public ");
    w.print(targetType.getQualifiedSourceName());
    w.println(" fromJson(Object in) {");
    w.indent();
    if (targetType.isAbstract()) {
      w.println("throw new UnsupportedOperationException();");
    } else {
      w.println("if (in == null) return null;");
      w.println("final JavaScriptObject jso = (JavaScriptObject)in;");
      w.print("final ");
      w.print(targetType.getQualifiedSourceName());
      w.print(" dst = new ");
      w.println(targetType.getQualifiedSourceName() + "();");
      w.println("fromJsonImpl(jso, dst);");
      w.println("return dst;");
    }
    w.outdent();
    w.println("}");
    w.println();

    w.print("protected void fromJsonImpl(JavaScriptObject jso,");
    w.print(targetType.getQualifiedSourceName());
    w.println(" dst) {");
    w.indent();

    if (needsSuperSerializer(targetType)) {
      w.print("super.fromJsonImpl(jso, (");
      w.print(targetType.getSuperclass().getQualifiedSourceName());
      w.println(")dst);");
    }

    for (final JField f : sortFields(targetType)) {
      final String doget = "jsonGet_" + f.getName() + "(jso)";
      final String doset0, doset1;

      if (f.isPrivate()) {
        doset0 = "objectSet_" + f.getName() + "(dst, ";
        doset1 = ")";
      } else {
        doset0 = "dst." + f.getName() + " = ";
        doset1 = "";
      }

      JType type = f.getType();
	  if (type.isArray() != null) {
        final JType ct = type.isArray().getComponentType();
        w.println("if (" + doget + " != null) {");
        w.indent();

        w.print("final ");
        w.print(ct.getQualifiedSourceName());
        w.print("[] tmp = new ");
        w.print(ct.getQualifiedSourceName());
        w.print("[");
        w.print(ObjectArraySerializer.class.getName());
        w.print(".size(" + doget + ")");
        w.println("];");

        w.println("ser_" + f.getName() + ".fromJson(" + doget + ", tmp);");

        w.print(doset0);
        w.print("tmp");
        w.print(doset1);
        w.println(";");

        w.outdent();
        w.println("}");

      } else if (isJsonPrimitive(type)) {
        w.print(doset0);
        w.print(doget);
        w.print(doset1);
        w.println(";");

      } else if (isBoxedPrimitive(type)) {
        w.print(doset0);
        w.print("( " + doget + " != null) ? ");
        //w.print("new " + type.getQualifiedSourceName() + "(");
        w.print(doget);
        //w.print(")");
        w.print(":null");
        w.print(doset1);
        w.println(";");

      } else {
        w.print(doset0);
        if (needsTypeParameter(type)) {
          w.print("ser_" + f.getName());
        } else {
          String serializerFor = serializerFor(type);
          w.print(serializerFor + ".INSTANCE");
        }
        w.print(".fromJson(" + doget + ")");
        w.print(doset1);
        w.println(";");
      }
    }

    w.outdent();
    w.println("}");
    w.println();
  }

  static boolean isJsonPrimitive(final JType t) {
    return t.isPrimitive() != null || isJsonString(t);
  }

  static boolean isBoxedPrimitive(final JType t) {
    final String qsn = t.getQualifiedSourceName();
    return qsn.equals(Boolean.class.getCanonicalName())
        || qsn.equals(Byte.class.getCanonicalName()) || isBoxedCharacter(t)
        || qsn.equals(Double.class.getCanonicalName())
        || qsn.equals(Float.class.getCanonicalName())
        || qsn.equals(Integer.class.getCanonicalName())
        || qsn.equals(Short.class.getCanonicalName());
  }

  static boolean isBoxedCharacter(JType t) {
    return t.getQualifiedSourceName()
        .equals(Character.class.getCanonicalName());
  }

  private String boxedTypeToPrimitiveTypeName(JType t) {
    final String qsn = t.getQualifiedSourceName();
    if (qsn.equals(Boolean.class.getCanonicalName())) return "Boolean";
    if (qsn.equals(Byte.class.getCanonicalName())) return "Byte";
    if (qsn.equals(Character.class.getCanonicalName()))
      return "java.lang.String";
    if (qsn.equals(Double.class.getCanonicalName())) return "Double";
    if (qsn.equals(Float.class.getCanonicalName())) return "Float";
    if (qsn.equals(Integer.class.getCanonicalName())) return "Integer";
    if (qsn.equals(Short.class.getCanonicalName())) return "Short";
    throw new IllegalArgumentException(t + " is not a boxed type");
  }

  static boolean isJsonString(final JType t) {
    return t.getQualifiedSourceName().equals(String.class.getCanonicalName());
  }

  private SourceWriter getSourceWriter(final TreeLogger logger,
      final GeneratorContext ctx) {
    final JPackage targetPkg = targetType.getPackage();
    final String pkgName = targetPkg == null ? "" : targetPkg.getName();
    final PrintWriter pw;
    final ClassSourceFileComposerFactory cf;

    pw = ctx.tryCreate(logger, pkgName, getSerializerSimpleName());
    if (pw == null) {
      return null;
    }

    cf = new ClassSourceFileComposerFactory(pkgName, getSerializerSimpleName());
    cf.addImport(JavaScriptObject.class.getCanonicalName());
    cf.addImport(JsonSerializer.class.getCanonicalName());
    if (targetType.isEnum() != null) {
      cf.addImport(EnumSerializer.class.getCanonicalName());
      cf.setSuperclass(EnumSerializer.class.getSimpleName() + "<"
          + targetType.getQualifiedSourceName() + ">");
    } else if (needsSuperSerializer(targetType)) {
      cf.setSuperclass(getSerializerQualifiedName(targetType.getSuperclass()));
    } else {
      cf.addImport(ObjectSerializer.class.getCanonicalName());
      cf.setSuperclass(ObjectSerializer.class.getSimpleName() + "<"
          + targetType.getQualifiedSourceName() + ">");
    }
    return cf.createSourceWriter(ctx, pw);
  }

  private static boolean needsSuperSerializer(JClassType type) {
    type = type.getSuperclass();
    while (!Object.class.getName().equals(type.getQualifiedSourceName())) {
      if (sortFields(type).length > 0) {
        return true;
      }
      type = type.getSuperclass();
    }
    return false;
  }

  private String getSerializerQualifiedName(final JClassType targetType) {
    final String[] name;
    name = ProxyCreator.synthesizeTopLevelClassName(targetType, SER_SUFFIX);
    return name[0].length() == 0 ? name[1] : name[0] + "." + name[1];
  }

  private String getSerializerSimpleName() {
    return ProxyCreator.synthesizeTopLevelClassName(targetType, SER_SUFFIX)[1];
  }

  static boolean needsTypeParameter(final JType ft) {
    return ft.isArray() != null
        || (ft.isParameterized() != null && parameterizedSerializers
            .containsKey(ft.getQualifiedSourceName()));
  }

  private static JField[] sortFields(final JClassType targetType) {
    final ArrayList<JField> r = new ArrayList<JField>();
    for (final JField f : targetType.getFields()) {
      if (!f.isStatic() && !f.isTransient() && !f.isFinal()) {
        r.add(f);
      }
    }
    Collections.sort(r, FIELD_COMP);
    return r.toArray(new JField[r.size()]);
  }
}
