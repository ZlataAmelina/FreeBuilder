package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mapper;
import static org.inferred.freebuilder.processor.BuilderMethods.nullableSetter;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * This property class handles the primitive optional fields, including
 * {@link OptionalDouble}, {@link OptionalLong}, and {@link OptionalInt}.
 */
public class PrimitiveOptionalProperty extends PropertyCodeGenerator {
  private final OptionalType optional;
  private final TypeMirror elementType;
  private final Property flag;

  @VisibleForTesting
  PrimitiveOptionalProperty(Datatype datatype, Property property) {
    super(datatype, property);
    this.elementType = property.getType();
    this.optional = OptionalType.lookup(this.elementType);
    this.flag = Property.Builder.from(property).setName(property.getName() + "Valid")
                                         .build();
  }

  @Override
  public Initially initialState() {
    return Initially.OPTIONAL;
  }

  @Override
  public void addValueFieldDeclaration(SourceBuilder code, FieldAccess finalField) {
    code.addLine("private final %s %s;", optional.wrapper, finalField);
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private %s %s;", optional.primitiveType, property.getField())
        .addLine("private boolean %s = false;", flag.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addSetter(code);
    addOptionalSetter(code);
    addMapper(code);
    addClear(code);
    addGetter(code);
  }

  private void addSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s(%s %s) {",
            datatype.getBuilder(), setter(property), optional.primitiveType, property.getName())
        .addLine("  %s = %s;", property.getField(), property.getName())
        .addLine("  %s = true;", flag.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addOptionalSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s %s) {",
            datatype.getBuilder(), setter(property), optional.cls, property.getName())
        .addLine("  if (%s.isPresent()) {", property.getName())
        .addLine("    return %s(%s.%s());", setter(property),
            property.getName(), optional.getAsMethod)
        .addLine("  } else {")
        .addLine("    return %s();", clearMethod(property))
        .addLine("  }")
        .addLine("}");
  }

  private void addMapper(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * If the value to be returned by %s is present,",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * replaces it by applying {@code mapper} to it and using the result.")
        .addLine(" *")
        .addLine(" * <p>If the result is null, clears the value.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mapper} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mapper) {",
            datatype.getBuilder(), mapper(property), optional.unaryType)
        .addLine("  %s().ifPresent(value -> %s(mapper.%s(value)));", getter(property),
            setter(property), optional.unaryMethod)
        .addLine("  return (%s) this;", datatype.getBuilder());
    code.addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s",
                 datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * to {@link %1$s#empty() %2$s}.", optional.cls, optional.empty)
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", datatype.getBuilder(), clearMethod(property))
        .addLine("  %s = false;", flag.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns the value that will be returned by %s.",
                 datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" */")
        .addLine("public %s %s() {", property.getType(), getter(property));
    code.add("return %s ? %s.of(%s) : %s;\n", flag.getField(), optional.cls,
             property.getField(), optional.empty)
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.addLine("%s = %s ? %s : null;", finalField, flag.getField().on(builder),
                 property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(SourceBuilder code, String value) {
    String propertyValue = value + "." + property.getGetterName() + "()";
    optional.invokeIfPresent(code, propertyValue, setter(property));
  }

  @Override
  public void addMergeFromBuilder(SourceBuilder code, String builder) {
    String propertyValue = builder + "." + getter(property) + "()";
    optional.invokeIfPresent(code, propertyValue, setter(property));
  }

  @Override
  public void addSetBuilderFromPartial(SourceBuilder code, Variable builder) {
    code.addLine("%s.%s(%s);", builder, nullableSetter(property), property.getField());
  }

  @Override
  public void addReadValueFragment(SourceBuilder code, Excerpt finalField) {
    code.add("%s == null ? %s : %s.of(%s)", finalField, optional.empty, optional.cls,
             finalField);
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, setter(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    Optional<Variable> defaults = Declarations.freshBuilder(code, datatype);
    if (defaults.isPresent()) {
      code.addLine("%s(%s.%s());", setter(property), defaults.get(), getter(property));
    } else {
      code.addLine("%s = false;", flag.getField());
    }
  }

  @VisibleForTesting
  enum OptionalType {
    INT(QualifiedName.of("java.util", "OptionalInt"), Integer.class, "int"),
    LONG(QualifiedName.of("java.util", "OptionalLong"), Long.class, "long"),
    DOUBLE(QualifiedName.of("java.util", "OptionalDouble"), Double.class, "double");

    private final QualifiedName cls;
    private final QualifiedName wrapper;
    private final String empty;
    private final String primitiveType;
    private final QualifiedName unaryType;
    private final String unaryMethod;
    private final String getAsMethod;

    OptionalType(QualifiedName qualifiedName, Class<?> wrapper, String primitiveType) {
      this.cls = qualifiedName;
      this.wrapper = QualifiedName.of(wrapper);
      this.primitiveType = primitiveType;

      String upperCamelName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, primitiveType);
      this.unaryType = QualifiedName.of("java.util.function", upperCamelName + "UnaryOperator");
      this.unaryMethod = "applyAs" + upperCamelName;
      this.getAsMethod = "getAs" + upperCamelName;
      this.empty = qualifiedName.getSimpleName() + ".empty()";
    }

    public static OptionalType lookup(TypeMirror elementType) {
      String type = elementType.toString();

      for (OptionalType op : values()) {
        if (type.contains(op.cls.getSimpleName())) {
          return op;
        }
      }

      throw new IllegalStateException("Not a supported type: " + type);
    }

    protected void invokeIfPresent(SourceBuilder code, String value, String method) {
      code.addLine("%s.ifPresent(this::%s);", value, method);
    }
  }

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<PrimitiveOptionalProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orElse(null);
      if (type == null) {
        return Optional.empty();
      }

      OptionalType optionalType = maybeOptional(type).orElse(null);
      if (optionalType == null) {
        return Optional.empty();
      }

      return Optional.of(new PrimitiveOptionalProperty(
        config.getDatatype(),
        config.getProperty()));
    }

    private static Optional<OptionalType> maybeOptional(DeclaredType type) {
      for (OptionalType optionalType : OptionalType.values()) {
        if (erasesToAnyOf(type, optionalType.cls)) {
          return Optional.of(optionalType);
        }
      }
      return Optional.empty();
    }
  }
}
