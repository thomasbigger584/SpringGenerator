package com.twb.create.test;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;

import javax.lang.model.element.Modifier;
import javax.persistence.EntityManager;

public class CreateDataUtil {

    private final GenerationOptions options;

    public CreateDataUtil(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

        final String resourceTestPackage = packageName + ".web.rest." + extensionPrefix.toLowerCase() + "." + entityName.toLowerCase();
        final String entityDataUtilResource = entityName + "DataUtil";

        FieldSpec defaultValueFieldSpec = FieldSpec.builder(String.class, "DEFAULT_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).
                initializer("\"CCCCCCCCCC\"").build();
        FieldSpec updatedValueFieldSpec = FieldSpec.builder(String.class, "UPDATED_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).
                initializer("\"DDDDDDDDD\"").build();

        final ClassName entityClassName = ClassName.get(packageName + ".domain", entityName);

        String dtoPackage = packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase();
        final ClassName createEntityClassName = ClassName.get(dtoPackage, "Create" + entityName + "DTO");
        final ClassName updateEntityClassName = ClassName.get(dtoPackage, "Update" + entityName + "DTO");

        MethodSpec createEntityWithObjectMethodSpec = MethodSpec.methodBuilder("com/twb/create" + entityName + "Entity").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                returns(entityClassName).
                addParameter(EntityManager.class, "em").
                addParameter(Object.class, "parent").
                addParameter(Boolean.class, "deleted").
                addStatement("$T entity = new $T()", entityClassName, entityClassName).
                addComment("entity.setParent(parent)").
                addComment("entity.setDeleted(deleted)").
                addComment("entity.setName(DEFAULT_NAME)").
                addStatement("em.persist(entity)").
                addComment("parent.getEntities().add(entity)").
                addStatement("return entity").
                build();

        MethodSpec createEntityMethodSpec = MethodSpec.methodBuilder("com/twb/create" + entityName + "Entity").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                returns(entityClassName).
                addParameter(EntityManager.class, "em").
                addParameter(Boolean.class, "deleted").
                addStatement("$T entity = new $T()", Object.class, Object.class).
                addStatement("return com.twb.create" + entityName + "Entity(em, entity, deleted)").
                build();

        MethodSpec createCreateDtoMethodSpec = MethodSpec.methodBuilder("createCreate" + entityName + "EntityDTO").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                addParameter(Long.class, "parentId").
                returns(createEntityClassName).
                addStatement("$T createEntityDto = new $T()", createEntityClassName, createEntityClassName).
                addComment("createEntityDto.setParentId(parentId)").
                addComment("createEntityDto.setName(DEFAULT_NAME)").
                addStatement("return createEntityDto").
                build();

        MethodSpec createUpdateDtoMethodSpec = MethodSpec.methodBuilder("createUpdate" + entityName + "EntityDTO").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                addParameter(Long.class, "id").
                returns(updateEntityClassName).
                addStatement("$T updateEntityDto = new $T()", updateEntityClassName, updateEntityClassName).
                addStatement("updateEntityDto.setId(id)").
                addComment("updateEntityDto.setName(UPDATED_NAME)").
                addStatement("return updateEntityDto").
                build();

        TypeSpec testResourceTypeSpec = TypeSpec.classBuilder(entityDataUtilResource).
                addModifiers(Modifier.PUBLIC).
                addField(defaultValueFieldSpec).
                addField(updatedValueFieldSpec).
                addMethod(createEntityWithObjectMethodSpec).
                addMethod(createEntityMethodSpec).
                addMethod(createCreateDtoMethodSpec).
                addMethod(createUpdateDtoMethodSpec).
                build();

        return JavaPoetUtil.buildJavaFile(resourceTestPackage, testResourceTypeSpec);
    }
}
