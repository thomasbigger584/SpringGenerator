package com.twb.create;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;

import javax.lang.model.element.Modifier;

public class CreateException {

    private final GenerationOptions options;

    public CreateException(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

        final String errorPackage = packageName + ".web.rest.errors." + extensionPrefix.toLowerCase();
        String exceptionName = entityName + "NotFoundException";

        final ClassName superExceptionClassName =
                ClassName.get("org.zalando.problem", "AbstractThrowableProblem");
        final ClassName statusClassName = ClassName.get("org.zalando.problem", "Status");
        final ClassName constantsClassName = ClassName.get(packageName + ".web.rest.errors", "ErrorConstants");

        MethodSpec constructorBuilder = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addStatement("super($T.DEFAULT_TYPE, \"" + entityName + " not found\", $T.NOT_FOUND)", constantsClassName, statusClassName).build();

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(exceptionName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(superExceptionClassName)
                .addMethod(constructorBuilder)
                .build();

        return JavaPoetUtil.buildJavaFile(errorPackage, jpaEntityTypeSpec);
    }
}
