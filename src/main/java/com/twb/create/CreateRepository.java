package com.twb.create;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Optional;

public class CreateRepository {

    private final GenerationOptions options;

    public CreateRepository(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

        String repositoryPackage = packageName + ".repository." + extensionPrefix.toLowerCase();
        String entityRepository = extensionPrefix + entityName + "Repository";

        final ClassName superRepoClassName =
                ClassName.get(packageName + ".repository", entityName + "Repository");

        final ClassName entityClassName = ClassName.get(packageName + ".domain", entityName);
        ParameterizedTypeName optionalEntityTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), entityClassName);
        ParameterizedTypeName pagedEntityTypeName = ParameterizedTypeName.get(ClassName.get(Page.class), entityClassName);
        ParameterizedTypeName listEntityTypeName = ParameterizedTypeName.get(ClassName.get(List.class), entityClassName);

        CreateQuery createQuery = new CreateQuery(entityName);

        MethodSpec findOneMethod = MethodSpec.methodBuilder("findById").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(Override.class).
                addAnnotation(AnnotationSpec.builder(Query.class).
                        addMember("value", createQuery.createFindByIdQuery()).build()).
                returns(optionalEntityTypeName).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(Param.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                build();

        MethodSpec findAllPagedMethod = MethodSpec.methodBuilder("findAll").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(Override.class).
                addAnnotation(AnnotationSpec.builder(Query.class).
                        addMember("value", createQuery.createFindAllQuery()).build()).
                returns(pagedEntityTypeName).
                addParameter(ParameterSpec.builder(Pageable.class, "pageable").build()).
                build();

        MethodSpec findAllListMethod = MethodSpec.methodBuilder("findAll").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(Override.class).
                addAnnotation(AnnotationSpec.builder(Query.class).
                        addMember("value", createQuery.createFindAllQuery()).build()).
                returns(listEntityTypeName).
                build();

        MethodSpec findOneDeletedMethod = MethodSpec.methodBuilder("findDeletedById").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(AnnotationSpec.builder(Query.class).
                        addMember("value", createQuery.createFileDeletedByIdQuery()).build()).
                returns(optionalEntityTypeName).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(Param.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                build();

        MethodSpec findAllDeletedMethod = MethodSpec.methodBuilder("findAllDeleted").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(AnnotationSpec.builder(Query.class).
                        addMember("value", createQuery.createFindAllDeletedQuery()).build()).
                returns(pagedEntityTypeName).
                addParameter(ParameterSpec.builder(Pageable.class, "pageable").build()).
                build();

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(superRepoClassName)
                .addAnnotation(Repository.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build())
                .addMethod(findOneMethod)
                .addMethod(findAllPagedMethod)
                .addMethod(findAllListMethod)
                .addMethod(findOneDeletedMethod)
                .addMethod(findAllDeletedMethod)
                .build();

        return JavaPoetUtil.buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }
}
