package com.twb.create;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.lang.model.element.Modifier;

public class CreateService {

    private final GenerationOptions options;

    public CreateService(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();
        boolean supportsElasticSearch = options.isSupportsElasticSearch();

        String servicePackage = packageName + ".service." + extensionPrefix.toLowerCase();
        String entityService = extensionPrefix + entityName + "Service";

        final ClassName superRepositoryClassName =
                ClassName.get(packageName + ".repository", entityName + "Repository");
        final String superRepositoryVarName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Repository";

        final ClassName repositoryClassName =
                ClassName.get(packageName + ".repository." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Repository");
        final String repositoryVarName = extensionPrefix.toLowerCase() + entityName + "Repository";

        FieldSpec repositoryField = FieldSpec.builder(repositoryClassName,
                repositoryVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        final ClassName repositorySearchClassName =
                ClassName.get(packageName + ".repository.search." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "SearchRepository");
        final String repositorySearchVarName = extensionPrefix.toLowerCase() + entityName + "SearchRepository";

        FieldSpec repositorySearchField = FieldSpec.builder(repositorySearchClassName,
                repositorySearchVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        final ClassName mapperClassName =
                ClassName.get(packageName + ".service.mapper." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Mapper");
        final String mapperVarName = extensionPrefix.toLowerCase() + entityName + "Mapper";

        FieldSpec mapperField = FieldSpec.builder(mapperClassName,
                mapperVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"SpringJavaInjectionPointsAutowiringInspection\"").build()).
                addParameter(superRepositoryClassName, superRepositoryVarName).
                addParameter(repositoryClassName, repositoryVarName);

        String superStatement = "super(%s)";
        String parameters = superRepositoryVarName + ((supportsElasticSearch) ? ", " + repositorySearchVarName : "");
        superStatement = String.format(superStatement, parameters);
        constructorBuilder.addStatement(superStatement);

        if (supportsElasticSearch) {
            constructorBuilder.addParameter(repositorySearchClassName, repositorySearchVarName).
                    addStatement("this." + repositorySearchVarName + " = " + repositorySearchVarName);
        }

        constructorBuilder.addParameter(mapperClassName, mapperVarName).
                addStatement("this." + repositoryVarName + " = " + repositoryVarName).
                addStatement("this." + mapperVarName + " = " + mapperVarName);

        MethodSpec constructor = constructorBuilder.build();

        ClassName getDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Get" + entityName + "DTO");
        ClassName createDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Create" + entityName + "DTO");
        ClassName updateDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Update" + entityName + "DTO");
        String createDtoVarName = "com/twb/create" + entityName + "Dto";
        String updateDtoVarName = "update" + entityName + "Dto";

        final ClassName entityClassName =
                ClassName.get(packageName + ".domain", entityName);

        String entityVarName = entityName.substring(0, 1).toLowerCase() + entityName.substring(1);
        MethodSpec createMethodSpec = MethodSpec.methodBuilder("com/twb/create").
                addModifiers(Modifier.PUBLIC).
                returns(getDtoClassName).
                addParameter(createDtoClassName, createDtoVarName).
                addCode(CodeBlock.builder().
                        addStatement("$T $N = $N.createDtoToEntity($N)", entityClassName, entityVarName, mapperVarName, createDtoVarName).
                        addStatement("// " + entityVarName + ".setDeleted(false);").
                        addStatement("$N = save($N)", entityVarName, entityVarName).
                        addStatement("return $N.entityToGetDto($N)", mapperVarName, entityVarName).
                        build()).
                build();

        ClassName entityException = ClassName.get(packageName + ".web.rest.errors." + extensionPrefix.toLowerCase(), entityName + "NotFoundException");

        MethodSpec updateMethodSpec = MethodSpec.methodBuilder("update").
                addModifiers(Modifier.PUBLIC).
                returns(getDtoClassName).
                addParameter(updateDtoClassName, updateDtoVarName).
                addCode(CodeBlock.builder()
                        .addStatement("$T result = findByIdThrowException($N.getId())", entityClassName, updateDtoVarName)
                        .addStatement("result = $N.updateEntity($N, result)", mapperVarName, updateDtoVarName)
                        .addStatement("return $N.entityToGetDto(result)", mapperVarName).build()).build();

        MethodSpec deleteThrowExceptionMethodSpec = MethodSpec.methodBuilder("markDeleted").
                addModifiers(Modifier.PUBLIC).
                addParameter(Long.class, "id").
                addStatement("$T result = findByIdThrowException(id)", entityClassName).
                addComment("result.setDeleted(true)").
                build();

        MethodSpec findByIdThrowExceptionMethodSpec = MethodSpec.methodBuilder("findByIdThrowException").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(entityClassName).
                addParameter(Long.class, "id").
                addCode(CodeBlock.builder().
                        add("return " + repositoryVarName + ".findById(id).orElseGet(() -> {\n").
                        indent().add("throw new $T();\n", entityException).unindent().
                        add("});\n").build()).
                build();

        MethodSpec findDeletedByIdThrowExceptionMethodSpec = MethodSpec.methodBuilder("findDeletedByIdThrowException").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(entityClassName).
                addParameter(Long.class, "id").
                addCode(CodeBlock.builder().
                        add("return " + repositoryVarName + ".findDeletedById(id).orElseGet(() -> {\n").
                        indent().add("throw new $T();\n", entityException).unindent().
                        add("});\n").build()).
                build();

        MethodSpec findDtoThrowExceptionMethodSpec = MethodSpec.methodBuilder("getById").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(getDtoClassName).
                addParameter(Long.class, "id").
                addStatement("$T result = findByIdThrowException(id)", entityClassName).
                addStatement("return $N.entityToGetDto(result)", mapperVarName).
                build();

        ParameterizedTypeName pagedDtoTypeName = ParameterizedTypeName.get(ClassName.get(Page.class), getDtoClassName);
        ParameterizedTypeName pagedEntityTypeName = ParameterizedTypeName.get(ClassName.get(Page.class), entityClassName);
        MethodSpec pagedDtoMethodSpec = MethodSpec.methodBuilder("getAll").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(pagedDtoTypeName).
                addParameter(Pageable.class, "pageable").
                addStatement("$T page = " + repositoryVarName + ".findAll(pageable)", pagedEntityTypeName).
                addStatement("return page.map($N::entityToGetDto)", mapperVarName).
                build();


        MethodSpec findDeletedDtoThrowExceptionMethodSpec = MethodSpec.methodBuilder("getDeletedById").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(getDtoClassName).
                addParameter(Long.class, "id").
                addStatement("$T result = findDeletedByIdThrowException(id)", entityClassName).
                addStatement("return $N.entityToGetDto(result)", mapperVarName).
                build();

        MethodSpec pagedDeletedDtoMethodSpec = MethodSpec.methodBuilder("getAllDeleted").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(pagedDtoTypeName).
                addParameter(Pageable.class, "pageable").
                addStatement("$T page = " + repositoryVarName + ".findAllDeleted(pageable)", pagedEntityTypeName).
                addStatement("return page.map($N::entityToGetDto)", mapperVarName).
                build();

        MethodSpec recoverDeletedMethodSpec = MethodSpec.methodBuilder("recoverById").
                addModifiers(Modifier.PUBLIC).
                returns(getDtoClassName).
                addParameter(Long.class, "id").
                addCode(CodeBlock.builder().
                        add("return findOne(id).map(" + entityVarName + " -> {\n").
                        indent().add("// " + entityVarName + ".setDeleted(false);\n").
                        add("return " + mapperVarName + ".entityToGetDto(" + entityVarName + ");\n").
                        unindent().add("}).orElseGet(() -> {\n").
                        indent().add("throw new $T();\n", entityException).unindent().
                        add("});\n").build()).
                build();

        final ClassName superServiceClassName =
                ClassName.get(packageName + ".service", entityName + "Service");
        TypeSpec.Builder jpaEntityTypeSpecBuilder = TypeSpec.classBuilder(entityService).
                addModifiers(Modifier.PUBLIC).
                addAnnotation(Service.class).
                addAnnotation(Transactional.class).
                addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build()).
                superclass(superServiceClassName).
                addField(repositoryField).
                addField(mapperField).
                addMethod(constructor).
                addMethod(createMethodSpec).
                addMethod(updateMethodSpec).
                addMethod(deleteThrowExceptionMethodSpec).
                addMethod(findByIdThrowExceptionMethodSpec).
                addMethod(findDeletedByIdThrowExceptionMethodSpec).
                addMethod(findDeletedDtoThrowExceptionMethodSpec).
                addMethod(findDtoThrowExceptionMethodSpec).
                addMethod(pagedDtoMethodSpec).
                addMethod(pagedDeletedDtoMethodSpec).
                addMethod(recoverDeletedMethodSpec);

        if (supportsElasticSearch) {
            jpaEntityTypeSpecBuilder.addField(repositorySearchField);
        }

        TypeSpec jpaEntityTypeSpec = jpaEntityTypeSpecBuilder.build();

        return JavaPoetUtil.buildJavaFile(servicePackage, jpaEntityTypeSpec);
    }
}
