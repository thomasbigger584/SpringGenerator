package com.twb.create;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;

import javax.lang.model.element.Modifier;

public class CreateMapper {

    private final GenerationOptions options;

    public CreateMapper(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

        String mapperPackage = packageName + ".service.mapper." + extensionPrefix.toLowerCase();
        String entityMapper = extensionPrefix + entityName + "Mapper";

        final ClassName entityClassName =
                ClassName.get(packageName + ".domain", entityName);

        ClassName createDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Create" + entityName + "DTO");
        MethodSpec createToEntityMethod = MethodSpec.methodBuilder("createDtoToEntity").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                returns(entityClassName).
                addParameter(createDtoClassName, "com/twb/create" + entityName + "Dto").
                build();

        ClassName getDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Get" + entityName + "DTO");

        ClassName updateDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Update" + entityName + "DTO");

        String entityVarName = entityName.substring(0, 1).toLowerCase() + entityName.substring(1);
        MethodSpec getToEntityMethod = MethodSpec.methodBuilder("entityToGetDto").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                returns(getDtoClassName).
                addParameter(entityClassName, entityVarName).
                build();

        MethodSpec updateEntityMethod = MethodSpec.methodBuilder("updateEntity").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addParameter(updateDtoClassName, "update" + entityName + "Dto").
                addParameter(ParameterSpec.builder(entityClassName, entityVarName).
                        addAnnotation(MappingTarget.class).build()).
                returns(entityClassName).
                build();

        TypeSpec mapperTypeSpec = TypeSpec.interfaceBuilder(entityMapper)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Mapper.class).
                        addMember("componentModel", "\"spring\"").
                        addMember("nullValueCheckStrategy", "ALWAYS").
                        addMember("uses", "{,}").
                        build())
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build())
                .addMethod(createToEntityMethod)
                .addMethod(getToEntityMethod)
                .addMethod(updateEntityMethod)
                .build();

        return JavaPoetUtil.buildJavaFile(mapperPackage, mapperTypeSpec).toBuilder().
                addStaticImport(NullValueCheckStrategy.class, "ALWAYS").
                build();
    }
}
