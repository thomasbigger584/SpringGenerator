package com.twb.create;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;
import lombok.Data;

import javax.lang.model.element.Modifier;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class CreateDto {

    public static final String PREFIX_GET = "Get";
    public static final String PREFIX_UPDATE = "Update";
    public static final String PREFIX_CREATE = "Create";

    private final GenerationOptions options;

    public CreateDto(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create(String dtoPrefix, boolean hasId) {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

        String dtoPackage = packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase();
        String entityDto = dtoPrefix + entityName + "DTO";

        TypeSpec.Builder dtoTypeSpecBuilder = TypeSpec.classBuilder(entityDto)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Data.class).
                        build());

        if (hasId) {
            FieldSpec idFieldSpec = FieldSpec.builder(Long.class, "id", Modifier.PRIVATE).
                    addAnnotation(AnnotationSpec.builder(Min.class).
                            addMember("value", "1L").build()).
                    addAnnotation(NotNull.class).
                    build();
            dtoTypeSpecBuilder.
                    addField(idFieldSpec);
        }

        return JavaPoetUtil.buildJavaFile(dtoPackage, dtoTypeSpecBuilder.build());
    }
}
