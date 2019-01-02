package com.twb.create;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;
import org.springframework.stereotype.Repository;

import javax.lang.model.element.Modifier;

public class CreateSearchRepository {

    private final GenerationOptions options;

    public CreateSearchRepository(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

        String repositoryPackage = packageName + ".repository.search." + extensionPrefix.toLowerCase();
        String entityRepository = extensionPrefix + entityName + "SearchRepository";

        final ClassName superRepoClassName =
                ClassName.get(packageName + ".repository.search", entityName + "SearchRepository");

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(superRepoClassName)
                .addAnnotation(Repository.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build())
                .build();

        return JavaPoetUtil.buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }
}
