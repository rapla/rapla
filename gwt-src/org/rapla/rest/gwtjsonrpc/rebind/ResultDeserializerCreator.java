// Copyright 2009 Google Inc.
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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.typeinfo.JArrayType;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;

import java.io.PrintWriter;
import java.util.HashMap;

import org.rapla.rest.gwtjsonrpc.client.impl.ArrayResultDeserializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ResultDeserializer;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.PrimitiveArrayResultDeserializers;
import org.rapla.rest.gwtjsonrpc.client.impl.ser.PrimitiveResultDeserializers;

/**
 * Creator of ResultDeserializers. Actually, only object arrays have created
 * deserializers:
 * <ul>
 * <li>Boxed primitives are handled by {@link PrimitiveResultDeserializers}
 * <li>Normal objects have their (generated) serializers extending
 * {@link com.google.gwtjsonrpc.client.ObjectSerializer}, that handle result
 * deserialisation as well.
 * <li>Arrays of (boxed) primitives are handled by
 * {@link PrimitiveArrayResultDeserializers}.
 * <li>And object arrays get a generated deserializer extending
 * {@link ArrayResultDeserializer}
 * </ul>
 * All object arrays that have a JSONSerializer for the array component can be
 * generated, but they will need to live in the same package as the serializer.
 * To do this, if the serializer lives in the
 * <code>com.google.gwtjsonrpc.client</code> package (where custom object
 * serializers live), the ResultDeserializer for it's array will be placed in
 * this package as well. Else it will be placed with the serializer in the
 * package the object lives.
 */
class ResultDeserializerCreator {
  private static final String DSER_SUFFIX = "_ResultDeserializer";

  private GeneratorContext context;
  private HashMap<String, String> generatedDeserializers;
  private SerializerCreator serializerCreator;

  private JArrayType targetType;
  private JType componentType;

  ResultDeserializerCreator(GeneratorContext c, SerializerCreator sc) {
    context = c;
    generatedDeserializers = new HashMap<String, String>();
    serializerCreator = sc;
  }

  void create(TreeLogger logger, JArrayType targetType) {
    this.targetType = targetType;
    this.componentType = targetType.getComponentType();

    if (componentType.isPrimitive() != null
        || SerializerCreator.isBoxedPrimitive(componentType)) {
      logger.log(TreeLogger.DEBUG,
          "No need to create array deserializer for primitive array "
              + targetType);
      return;
    }

    if (deserializerFor(targetType) != null) {
      return;
    }

    logger.log(TreeLogger.DEBUG, "Creating result deserializer for "
        + targetType.getSimpleSourceName());
    final SourceWriter srcWriter = getSourceWriter(logger, context);
    if (srcWriter == null) {
      return;
    }
    final String dsn = getDeserializerQualifiedName(targetType);
    generatedDeserializers.put(targetType.getQualifiedSourceName(), dsn);

    generateSingleton(srcWriter);
    generateInstanceMembers(srcWriter);
    generateFromResult(srcWriter);

    srcWriter.commit(logger);
  }

  private void generateSingleton(final SourceWriter w) {
    w.print("public static final ");
    w.print(getDeserializerSimpleName(targetType));
    w.print(" INSTANCE = new ");
    w.print(getDeserializerSimpleName(targetType));
    w.println("();");
    w.println();
  }

  private void generateInstanceMembers(SourceWriter w) {
    w.print("private final ");
    w.print(serializerCreator.serializerFor(targetType));
    w.print(" ");
    w.print("serializer");
    w.print(" = ");
    serializerCreator.generateSerializerReference(targetType, w, true);
    w.println(";");
    w.println();
  }

  private void generateFromResult(SourceWriter w) {
    final String ctn = componentType.getQualifiedSourceName();

    w.println("@Override");
    w.print("public " + ctn + "[] ");
    w.println("fromResult(JavaScriptObject responseObject) {");
    w.indent();

    w.print("final " + ctn + "[] tmp = new " + ctn);
    w.println("[getResultSize(responseObject)];");

    w.println("serializer.fromJson(getResult(responseObject), tmp);");
    w.println("return tmp;");
    w.outdent();

    w.println("}");
  }

  private String getDeserializerQualifiedName(JArrayType targetType) {
    final String pkgName = getDeserializerPackageName(targetType);
    final String className = getDeserializerSimpleName(targetType);
    return pkgName.length() == 0 ? className : pkgName + "." + className;
  }

  private String getDeserializerPackageName(JArrayType targetType) {
    // Place array deserializer in same package as the component deserializer
    final String compSerializer =
        serializerCreator.serializerFor(targetType.getComponentType());
    final int end = compSerializer.lastIndexOf('.');
    return end >= 0 ? compSerializer.substring(0, end) : "";
  }

  private static String getDeserializerSimpleName(JClassType targetType) {
    return ProxyCreator.synthesizeTopLevelClassName(targetType, DSER_SUFFIX)[1];
  }

  private SourceWriter getSourceWriter(TreeLogger logger,
      GeneratorContext context) {
    String pkgName = getDeserializerPackageName(targetType);
    final String simpleName = getDeserializerSimpleName(targetType);
    final PrintWriter pw;
    final ClassSourceFileComposerFactory cf;

    pw = context.tryCreate(logger, pkgName, simpleName);
    if (pw == null) {
      return null;
    }

    cf = new ClassSourceFileComposerFactory(pkgName, simpleName);
    cf.addImport(JavaScriptObject.class.getCanonicalName());
    cf.addImport(ResultDeserializer.class.getCanonicalName());

    cf.setSuperclass(ArrayResultDeserializer.class.getCanonicalName());
    cf.addImplementedInterface(ResultDeserializer.class.getCanonicalName()
        + "<" + targetType.getQualifiedSourceName() + ">");

    return cf.createSourceWriter(context, pw);
  }

  private String deserializerFor(JArrayType targetType) {
    final JType componentType = targetType.getComponentType();
    // Custom primitive deserializers
    if (SerializerCreator.isBoxedPrimitive(componentType))
      return PrimitiveArrayResultDeserializers.class.getCanonicalName() + "."
          + componentType.getSimpleSourceName().toUpperCase() + "_INSTANCE";
    final String name =
        generatedDeserializers.get(targetType.getQualifiedSourceName());

    return name == null ? null : name + ".INSTANCE";
  }

  public void generateDeserializerReference(JType targetType, SourceWriter w) {
    if (SerializerCreator.isBoxedPrimitive(targetType)) {
      w.print(PrimitiveResultDeserializers.class.getCanonicalName());
      w.print(".");
      w.print(targetType.getSimpleSourceName().toUpperCase());
      w.print("_INSTANCE");
    } else if (targetType.isArray() != null) {
      w.print(deserializerFor(targetType.isArray()));
    } else {
      serializerCreator.generateSerializerReference(targetType, w, false);
    }
  }
}
