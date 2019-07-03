package com.twb.create;

import com.squareup.javapoet.*;
import com.twb.util.GenerationOptions;
import com.twb.util.JavaPoetUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.lang.model.element.Modifier;
import javax.validation.Valid;
import java.util.List;

public class CreateResource {

    private final GenerationOptions options;

    public CreateResource(GenerationOptions options) {
        this.options = options;
    }

    public JavaFile create() {

        String packageName = options.getPackageName();
        String extensionPrefix = options.getExtensionPrefix();
        String entityName = options.getEntityName();

        final String repositoryPackage = packageName + ".web.rest." + extensionPrefix.toLowerCase();
        final String entityRepository = extensionPrefix + entityName + "Resource";

        final ClassName serviceClassName =
                ClassName.get(packageName + ".service.ext", "Ext" + entityName + "Service");
        final String serviceVarName = extensionPrefix.toLowerCase() + entityName + "Service";

        FieldSpec serviceField = FieldSpec.builder(serviceClassName,
                serviceVarName, Modifier.PRIVATE, Modifier.FINAL).build();

        MethodSpec constructor = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addParameter(serviceClassName, serviceVarName).
                addStatement("this." + serviceVarName + " = " + serviceVarName).
                build();

        String resourceUriName = getUrlPath(entityName);

        String entityVarName = entityName.substring(0, 1).toLowerCase() + entityName.substring(1);
        FieldSpec entityNameFieldSpec =
                FieldSpec.builder(String.class, "ENTITY_NAME", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).
                        initializer("\"" + entityVarName + "\"").build();

        ClassName createDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Create" + entityName + "DTO");
        String createDtoVarName = "create" + entityName + "Dto";

        ClassName updateDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Update" + entityName + "DTO");
        String updateDtoVarName = "update" + entityName + "Dto";

        ClassName getDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Get" + entityName + "DTO");
        ParameterizedTypeName getResponseEntityTypeName = ParameterizedTypeName.get(ClassName.get(ResponseEntity.class), getDtoClassName);

        ClassName headerUtilClassName = ClassName.get(packageName + ".web.rest.util", "HeaderUtil");

        MethodSpec createMethodSpec = MethodSpec.methodBuilder("create" + entityName).
                addAnnotation(PostMapping.class).
                addParameter(ParameterSpec.builder(createDtoClassName, createDtoVarName).
                        addAnnotation(Valid.class).
                        addAnnotation(RequestBody.class).build()).
                addStatement("$T result = $N.create($N)", getDtoClassName, serviceVarName, createDtoVarName).
                addStatement("return $T.status($T.CREATED)\n.body(result)", ResponseEntity.class, HttpStatus.class).
                returns(getResponseEntityTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        MethodSpec updateMethodSpec = MethodSpec.methodBuilder("update" + entityName).
                addAnnotation(PutMapping.class).
                addParameter(ParameterSpec.builder(updateDtoClassName, updateDtoVarName).
                        addAnnotation(Valid.class).
                        addAnnotation(RequestBody.class).build()).
                addStatement("$T result = $N.update($N)", getDtoClassName, serviceVarName, updateDtoVarName).
                addStatement("return $T.status($T.OK).body(result)", ResponseEntity.class, HttpStatus.class).
                returns(getResponseEntityTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        ParameterizedTypeName responseVoidTypeName = ParameterizedTypeName.get(ClassName.get(ResponseEntity.class), ClassName.get(Void.class));
        MethodSpec deleteMethodSpec = MethodSpec.methodBuilder("delete" + entityName).
                addAnnotation(AnnotationSpec.builder(DeleteMapping.class).
                        addMember("value", "\"/{id}\"").
                        build()).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(PathVariable.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                addStatement("$N.markDeleted(id)", serviceVarName).
                addStatement("return $T.status($T.NO_CONTENT).build()", ResponseEntity.class, HttpStatus.class).
                returns(responseVoidTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        MethodSpec getByIdMethodSpec = MethodSpec.methodBuilder("get" + entityName + "ById").
                addAnnotation(AnnotationSpec.builder(GetMapping.class).
                        addMember("value", "\"/{id}\"").
                        build()).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(PathVariable.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                addStatement("$T result = $N.getById(id)", getDtoClassName, serviceVarName).
                addStatement("return $T.status($T.OK).body(result)", ResponseEntity.class, HttpStatus.class).
                returns(getResponseEntityTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        ParameterizedTypeName listDtoTypeName = ParameterizedTypeName.get(ClassName.get(List.class), getDtoClassName);
        ParameterizedTypeName responseDtoTypeName = ParameterizedTypeName.get(ClassName.get(ResponseEntity.class), listDtoTypeName);
        ParameterizedTypeName pageDtoTypeName = ParameterizedTypeName.get(ClassName.get(Page.class), getDtoClassName);

        ClassName compBuilderClassName = ClassName.get(UriComponentsBuilder.class);

        ClassName paginationUtilClassName = ClassName.get("io.github.jhipster.web.util", "PaginationUtil");
        MethodSpec getAllDtoMethodSpec = MethodSpec.methodBuilder("getAll" + entityName).
                addAnnotation(GetMapping.class).
                addParameter(ParameterSpec.builder(Pageable.class, "pageable").build()).
                addStatement("$T page = $N.getAll(pageable)", pageDtoTypeName, serviceVarName).
                addStatement("$T uri = $T.fromUriString(\"/api/ext-$L\")", compBuilderClassName, compBuilderClassName, resourceUriName).
                addStatement("$T headers = $T.generatePaginationHttpHeaders(uri, page)", HttpHeaders.class, paginationUtilClassName).
                addStatement("return new $T<>(page.getContent(), headers, $T.OK)", ResponseEntity.class, HttpStatus.class).
                returns(responseDtoTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        MethodSpec getDeletedByIdMethodSpec = MethodSpec.methodBuilder("getDeleted" + entityName + "ById").
                addAnnotation(AnnotationSpec.builder(GetMapping.class).
                        addMember("value", "\"/deleted/{id}\"").
                        build()).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(PathVariable.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                addStatement("$T result = $N.getDeletedById(id)", getDtoClassName, serviceVarName).
                addStatement("return $T.status($T.OK).body(result)", ResponseEntity.class, HttpStatus.class).
                returns(getResponseEntityTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        MethodSpec getAllDeletedDtoMethodSpec = MethodSpec.methodBuilder("getAllDeleted" + entityName).
                addAnnotation(AnnotationSpec.builder(GetMapping.class).
                        addMember("value", "\"/deleted\"").
                        build()).
                addParameter(ParameterSpec.builder(Pageable.class, "pageable").build()).
                addStatement("$T page = $N.getAllDeleted(pageable)", pageDtoTypeName, serviceVarName).
                addStatement("$T uri = $T.fromUriString(\"/api/ext-$L/deleted\")", compBuilderClassName, compBuilderClassName, resourceUriName).
                addStatement("$T headers = $T.generatePaginationHttpHeaders(uri, page)",
                        HttpHeaders.class, paginationUtilClassName).
                addStatement("return new $T<>(page.getContent(), headers, $T.OK)", ResponseEntity.class, HttpStatus.class).
                returns(responseDtoTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        MethodSpec recoverByIdMethodSpec = MethodSpec.methodBuilder("recover" + entityName + "ById").
                addAnnotation(AnnotationSpec.builder(PostMapping.class).
                        addMember("value", "\"/recover/{id}\"").
                        build()).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(PathVariable.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                addStatement("$T result = $N.recoverById(id)", getDtoClassName, serviceVarName).
                addStatement("return $T.status($T.OK).body(result)", ResponseEntity.class, HttpStatus.class).
                returns(getResponseEntityTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityRepository).
                addModifiers(Modifier.PUBLIC).
                addAnnotation(RestController.class).
                addAnnotation(AnnotationSpec.builder(RequestMapping.class).
                        addMember("value", "\"/api/ext-" + resourceUriName.toLowerCase() + "\"").
                        build()).
                addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build()).
                addField(serviceField).
                addField(entityNameFieldSpec).
                addMethod(constructor).
                addMethod(createMethodSpec).
                addMethod(updateMethodSpec).
                addMethod(getByIdMethodSpec).
                addMethod(getAllDtoMethodSpec).
                addMethod(getDeletedByIdMethodSpec).
                addMethod(deleteMethodSpec).
                addMethod(getAllDeletedDtoMethodSpec).
                addMethod(recoverByIdMethodSpec).
                build();

        return JavaPoetUtil.buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private String getUrlPath(String entityName) {
        String resourceUriName = "";
        String[] words = entityName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        for (int index = 0; index < words.length; index++) {
            resourceUriName = resourceUriName.concat(words[index]);
            if (index < words.length - 1) {
                resourceUriName = resourceUriName.concat("-");
            }
        }
        return resourceUriName.toLowerCase();
    }
}
