import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.squareup.javapoet.*;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.lang.model.element.Modifier;
import javax.persistence.EntityManager;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SpringClassGenerator {

    @Parameter(names = "-e", description = "Entity to parse", required = true)
    private List<String> entities = new ArrayList<>();

    @Parameter(names = "--mc", description = "App Main class", required = true)
    private String appMainClass = null;

    @Parameter(names = "-p", converter = PathConverter.class, description = "Path to Project Path")
    private Path projectPath = Paths.get("/Users/thomasbigger/Desktop/projects/backend/cathedral-eye-backend");

    @Parameter(names = "--ep", description = "Extension Prefix for generated files")
    private String extensionPrefix = "Ext";

    @Parameter(names = "--pn", description = "Package name for generated files")
    private String packageName = "com.pa.twb";

    @Parameter(names = "--es", description = "Supports elastic search")
    private boolean supportsElasticSearch = false;

    @Parameter(names = "-d", description = "Turn on debugging")
    private boolean isDebug = false;

    @Parameter(names = "--help", help = true)
    private boolean help = false;

    @Parameter(names = "--st", description = "Skip Resource Test")
    private boolean skipTest = false;

    public static void main(String... args) {
        SpringClassGenerator scg = new SpringClassGenerator();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(scg)
                .build();
        jCommander.parse(args);

        if (scg.help) {
            jCommander.usage();
            return;
        }

        scg.generate();
    }

    private void generate() {

        for (String entityName : entities) {

            entityName = entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

            List<JavaFile> mainJavaFiles = new ArrayList<>();

            mainJavaFiles.add(createRepository(entityName));
            mainJavaFiles.add(createDto("Get", entityName, true));
            mainJavaFiles.add(createDto("Create", entityName, false));
            mainJavaFiles.add(createDto("Update", entityName, true));
            mainJavaFiles.add(createMapper(entityName));
            if (supportsElasticSearch) {
                mainJavaFiles.add(createSearchRespository(entityName));
            }
            mainJavaFiles.add(createException(entityName));
            mainJavaFiles.add(createService(entityName));
            mainJavaFiles.add(createResource(entityName));

            List<JavaFile> testJavaFiles = new ArrayList<>();
            if (!skipTest) {
                testJavaFiles.add(createDataUtil(entityName));
                testJavaFiles.add(createTest(entityName));
            }

            try {
                for (JavaFile mainJavaFile : mainJavaFiles) {
                    mainJavaFile.writeTo(Paths.get(projectPath.toString() + "/src/main/java/"));
                }
                for (JavaFile testJavaFile : testJavaFiles) {
                    testJavaFile.writeTo(Paths.get(projectPath.toString() + "/src/test/java/"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private JavaFile createRepository(String entityName) {

        String repositoryPackage = packageName + ".repository." + extensionPrefix.toLowerCase();
        String entityRepository = extensionPrefix + entityName + "Repository";

        final ClassName superRepoClassName =
                ClassName.get(packageName + ".repository", entityName + "Repository");

        final ClassName entityClassName = ClassName.get(packageName + ".domain", entityName);
        ParameterizedTypeName optionalEntityTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), entityClassName);
        ParameterizedTypeName pagedEntityTypeName = ParameterizedTypeName.get(ClassName.get(Page.class), entityClassName);
        String firstLetterAlias = String.valueOf(entityName.charAt(0)).toLowerCase();

        MethodSpec findOneMethod = MethodSpec.methodBuilder("findById").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(Override.class).
                addAnnotation(AnnotationSpec.builder(Query.class).addMember("value", "\"SELECT " + firstLetterAlias + " \" +\n" +
                        "\"FROM " + entityName + " " + firstLetterAlias + " \" +\n" +
                        "\"WHERE " + firstLetterAlias + ".deleted = FALSE \" +\n" +
                        "\"AND " + firstLetterAlias + ".id = :id\"").build()).
                returns(optionalEntityTypeName).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(Param.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                build();

        MethodSpec findAllMethod = MethodSpec.methodBuilder("findAll").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(Override.class).
                addAnnotation(AnnotationSpec.builder(Query.class).addMember("value", "\"SELECT " + firstLetterAlias + " \" +\n" +
                        "\"FROM " + entityName + " " + firstLetterAlias + " \" +\n" +
                        "\"WHERE " + firstLetterAlias + ".deleted = FALSE\"").build()).
                returns(pagedEntityTypeName).
                addParameter(ParameterSpec.builder(Pageable.class, "pageable").build()).
                build();

        MethodSpec findAllDeletedMethod = MethodSpec.methodBuilder("findAllDeleted").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                addAnnotation(AnnotationSpec.builder(Query.class).addMember("value", "\"SELECT " + firstLetterAlias + " \" +\n" +
                        "\"FROM " + entityName + " " + firstLetterAlias + " \" +\n" +
                        "\"WHERE " + firstLetterAlias + ".deleted = TRUE\"").build()).
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
                .addMethod(findAllMethod)
                .addMethod(findAllDeletedMethod)
                .build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private JavaFile createDto(String dtoPrefix, String entityName, boolean hasId) {

        String dtoPackage = packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase();
        String entityDto = dtoPrefix + entityName + "DTO";

        TypeSpec.Builder dtoTypeSpecBuilder = TypeSpec.classBuilder(entityDto)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build());

        if (hasId) {
            FieldSpec idFieldSpec = FieldSpec.builder(Long.class, "id", Modifier.PRIVATE).
                    addAnnotation(AnnotationSpec.builder(Min.class).
                            addMember("value", "1L").build()).
                    addAnnotation(NotNull.class).
                    build();
            dtoTypeSpecBuilder.
                    addField(idFieldSpec).
                    addMethod(MethodSpec.methodBuilder("getId").
                            addModifiers(Modifier.PUBLIC).
                            returns(Long.class).
                            addStatement("return id").build()).
                    addMethod(MethodSpec.methodBuilder("setId").
                            addModifiers(Modifier.PUBLIC).
                            addParameter(Long.class, "id").
                            addStatement("this.id = id").build());
        }

        return buildJavaFile(dtoPackage, dtoTypeSpecBuilder.build());
    }

    private JavaFile createMapper(String entityName) {

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
                addParameter(createDtoClassName, "create" + entityName + "Dto").
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

        return buildJavaFile(mapperPackage, mapperTypeSpec).toBuilder().
                addStaticImport(NullValueCheckStrategy.class, "ALWAYS").
                build();
    }

    private JavaFile createSearchRespository(String entityName) {

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

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private JavaFile createException(String entityName) {
        final String errorPackage = packageName + ".web.rest.errors." + extensionPrefix.toLowerCase();
        String exceptionName = entityName + "NotFoundException";

        final ClassName superExceptionClassName =
                ClassName.get("org.zalando.problem", "AbstractThrowableProblem");
        final ClassName statusClassName = ClassName.get("org.zalando.problem", "Status");
        final ClassName constantsClassName = ClassName.get(packageName + ".web.rest.errors", "ErrorConstants");

        MethodSpec constructorBuilder = MethodSpec.constructorBuilder().
                addModifiers(Modifier.PUBLIC).
                addStatement("super($T.ENTITY_NOT_FOUND_TYPE, \"" + entityName + " not found\", $T.NOT_FOUND)", constantsClassName, statusClassName).build();

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(exceptionName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(superExceptionClassName)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build())
                .addMethod(constructorBuilder)
                .build();

        return buildJavaFile(errorPackage, jpaEntityTypeSpec);
    }

    private JavaFile createService(String entityName) {

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
        String createDtoVarName = "create" + entityName + "Dto";
        String updateDtoVarName = "update" + entityName + "Dto";

        final ClassName entityClassName =
                ClassName.get(packageName + ".domain", entityName);

        String entityVarName = entityName.substring(0, 1).toLowerCase() + entityName.substring(1);
        MethodSpec createMethodSpec = MethodSpec.methodBuilder("create").
                addModifiers(Modifier.PUBLIC).
                returns(getDtoClassName).
                addParameter(createDtoClassName, createDtoVarName).
                addCode(CodeBlock.builder().
                        addStatement("$T $N = $N.createDtoToEntity($N)", entityClassName, entityVarName, mapperVarName, createDtoVarName).
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
                addMethod(findDtoThrowExceptionMethodSpec).
                addMethod(pagedDtoMethodSpec).
                addMethod(pagedDeletedDtoMethodSpec).
                addMethod(recoverDeletedMethodSpec);

        if (supportsElasticSearch) {
            jpaEntityTypeSpecBuilder.addField(repositorySearchField);
        }

        TypeSpec jpaEntityTypeSpec = jpaEntityTypeSpecBuilder.build();

        return buildJavaFile(servicePackage, jpaEntityTypeSpec);
    }

    private JavaFile createResource(String entityName) {

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
                addStatement("return $T.status($T.CREATED)\n.headers($T.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))\n.body(result)", ResponseEntity.class, HttpStatus.class, headerUtilClassName).
                returns(getResponseEntityTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        MethodSpec updateMethodSpec = MethodSpec.methodBuilder("update" + entityName).
                addAnnotation(PutMapping.class).
                addParameter(ParameterSpec.builder(updateDtoClassName, updateDtoVarName).
                        addAnnotation(Valid.class).
                        addAnnotation(RequestBody.class).build()).
                addStatement("$T result = $N.update($N)", getDtoClassName, serviceVarName, updateDtoVarName).
                addStatement("return $T.status($T.OK)\n.headers($T.createEntityUpdateAlert(ENTITY_NAME, result.getId().toString()))\n.body(result)", ResponseEntity.class, HttpStatus.class, headerUtilClassName).
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
                addStatement("return $T.status($T.NO_CONTENT)\n.headers($T.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build()", ResponseEntity.class, HttpStatus.class, headerUtilClassName).
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
        ClassName paginationUtilClassName = ClassName.get(packageName + ".web.rest.util", "PaginationUtil");
        MethodSpec getAllDtoMethodSpec = MethodSpec.methodBuilder("getAll" + entityName).
                addAnnotation(GetMapping.class).
                addParameter(ParameterSpec.builder(Pageable.class, "pageable").build()).
                addStatement("$T page = $N.getAll(pageable)", pageDtoTypeName, serviceVarName).
                addStatement("$T headers = $T.generatePaginationHttpHeaders(page, \"/api/ext-$L\")",
                        HttpHeaders.class, paginationUtilClassName, resourceUriName).
                addStatement("return new $T<>(page.getContent(), headers, $T.OK)", ResponseEntity.class, HttpStatus.class).
                returns(responseDtoTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        MethodSpec getAllDeletedDtoMethodSpec = MethodSpec.methodBuilder("getAllDeleted" + entityName).
                addAnnotation(AnnotationSpec.builder(GetMapping.class).
                        addMember("value", "\"/deleted\"").
                        build()).
                addParameter(ParameterSpec.builder(Pageable.class, "pageable").build()).
                addStatement("$T page = $N.getAllDeleted(pageable)", pageDtoTypeName, serviceVarName).
                addStatement("$T headers = $T.generatePaginationHttpHeaders(page, \"/api/ext-$L/deleted\")",
                        HttpHeaders.class, paginationUtilClassName, resourceUriName).
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
                addMethod(deleteMethodSpec).
                addMethod(getAllDeletedDtoMethodSpec).
                addMethod(recoverByIdMethodSpec).
                build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private JavaFile createDataUtil(String entityName) {

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

        MethodSpec createEntityWithObjectMethodSpec = MethodSpec.methodBuilder("create" + entityName + "Entity").
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

        MethodSpec createEntityMethodSpec = MethodSpec.methodBuilder("create" + entityName + "Entity").
                addModifiers(Modifier.PUBLIC, Modifier.STATIC).
                returns(entityClassName).
                addParameter(EntityManager.class, "em").
                addParameter(Boolean.class, "deleted").
                addStatement("$T entity = new $T()", Object.class, Object.class).
                addStatement("return create" + entityName + "Entity(em, entity, deleted)").
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

        return buildJavaFile(resourceTestPackage, testResourceTypeSpec);
    }

    private JavaFile createTest(String entityName) {

        final String resourceTestPackage = packageName + ".web.rest." + extensionPrefix.toLowerCase() + "." + entityName.toLowerCase();
        final String entityTestResource = extensionPrefix + entityName + "ResourceIntTest";

        final ClassName repoClassName =
                ClassName.get(packageName + ".repository." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Repository");
        final String repoVarName = extensionPrefix.toLowerCase() + entityName + "Repository";
        FieldSpec repoFieldSpec = FieldSpec.builder(repoClassName, repoVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName serviceClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Service");
        final String serviceVarName = extensionPrefix.toLowerCase() + entityName + "Service";
        FieldSpec serviceFieldSpec = FieldSpec.builder(serviceClassName, serviceVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName jacksonClassName = ClassName.get(MappingJackson2HttpMessageConverter.class);
        final String jacksonVarName = "jacksonMessageConverter";
        FieldSpec jacksonFieldSpec = FieldSpec.builder(jacksonClassName, jacksonVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName pageableClassName = ClassName.get(PageableHandlerMethodArgumentResolver.class);
        final String pageableVarName = "pageableArgumentResolver";
        FieldSpec pageableFieldSpec = FieldSpec.builder(pageableClassName, pageableVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        ClassName exceptionClassName = ClassName.get(packageName + ".web.rest.errors", "ExceptionTranslator");
        final String exceptionVarName = "exceptionTranslator";
        FieldSpec exceptionFieldSpec = FieldSpec.builder(exceptionClassName, exceptionVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName entityManagerClassName = ClassName.get(EntityManager.class);
        final String entityManagerVarName = "em";
        FieldSpec entityManagerFieldSpec = FieldSpec.builder(entityManagerClassName, entityManagerVarName, Modifier.PRIVATE).
                addAnnotation(Autowired.class).build();

        final ClassName restMvcClassName = ClassName.get(MockMvc.class);
        final String restMvcVarName = "rest" + entityName + "MockMvc";
        FieldSpec restMvcFieldSpec = FieldSpec.builder(restMvcClassName, restMvcVarName, Modifier.PRIVATE).build();

        final ClassName entityClassName = ClassName.get(packageName + ".domain", entityName);
        final ClassName resourceClassName =
                ClassName.get(packageName + ".web.rest." + extensionPrefix.toLowerCase(), extensionPrefix + entityName + "Resource");
        final String resourceVarName = extensionPrefix.toLowerCase() + entityName + "Resource";

        ClassName testUtilClassName = ClassName.get(packageName + ".web.rest", "TestUtil");
        MethodSpec setupMethodSpec = MethodSpec.methodBuilder("setup").
                addAnnotation(Before.class).
                addModifiers(Modifier.PUBLIC).
                addStatement("$T.initMocks(this)", MockitoAnnotations.class).
                addStatement("final $T " + resourceVarName + " = new $T(" + serviceVarName + ")", resourceClassName, resourceClassName).
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + " = $T.standaloneSetup(" + resourceVarName + ")\n", MockMvcBuilders.class).
                        indent().add(".setCustomArgumentResolvers(pageableArgumentResolver)\n").
                        add(".setConversionService(createFormattingConversionService())\n").
                        add(".setMessageConverters(jacksonMessageConverter).build();\n").
                        unindent().build()).
                build();

        /*
         * Tests
         * ******************************************************************
         */

        String baseApiUrl = "/api/" + extensionPrefix.toLowerCase() + "-" + getUrlPath(entityName);
        ParameterizedTypeName listEntityTypeName = ParameterizedTypeName.get(ClassName.get(List.class), entityClassName);

        MethodSpec testEntityCreateMethodSpec = MethodSpec.methodBuilder("testCreate" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addStatement("int databaseSizeBeforeCreate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createCreate" + entityName + "EntityDTO(1L)))) //update\n", testUtilClassName).
                        add(".andDo(print())\n").
                        add(".andExpect(status().isCreated());\n\n").
                        unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeCreate + 1)").
                addStatement("$T test = list.get(list.size() - 1)", entityClassName).
                addComment("assertThat(test.getName()).isEqualTo(DEFAULT_NAME)").
                build();

        ClassName entityException = ClassName.get(packageName + ".web.rest.errors." + extensionPrefix.toLowerCase(), entityName + "NotFoundException");
        MethodSpec testEntityCreateInvalidParentMethodSpec = MethodSpec.methodBuilder("testCreate" + entityName + "InvalidParent").
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addComment("some database setup\n").
                addStatement("int databaseSizeBeforeCreate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createCreate" + entityName + "EntityDTO($T.MAX_VALUE))))\n", testUtilClassName, Long.class).
                        add(".andExpect(status().isCreated())).\n").
                        unindent().add("hasCause(new $T());\n\n", entityException).unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeCreate)").
                build();

        MethodSpec tesUpdateEntityMethodSpec = MethodSpec.methodBuilder("testUpdate" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addStatement("int databaseSizeBeforeUpdate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(put(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createUpdate" + entityName + "EntityDTO(1L)))) //update\n", testUtilClassName).
                        add(".andDo(print())\n").
                        add(".andExpect(status().isOk());\n\n").
                        unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeUpdate)").
                addStatement("$T test = list.get(list.size() - 1)", entityClassName).
                addComment("assertThat(test.getName()).isEqualTo(UPDATED_NAME)").
                build();

        MethodSpec updateNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testUpdateNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addStatement("int databaseSizeBeforeUpdate = " + repoVarName + ".findAll().size()").
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(put(\"" + baseApiUrl + "\")\n").
                        indent().add(".contentType($T.APPLICATION_JSON_UTF8)\n", testUtilClassName).
                        add(".content($T.convertObjectToJsonBytes(createUpdate" + entityName + "EntityDTO($T.MAX_VALUE))))\n", testUtilClassName, Long.class).
                        add(".andExpect(status().isOk())).\n").
                        unindent().add("hasCause(new $T());\n\n", entityException).unindent().build()).
                addStatement("$T list = " + repoVarName + ".findAll()", listEntityTypeName).
                addStatement("assertThat(list).hasSize(databaseSizeBeforeUpdate)").
                build();

        MethodSpec testGetEntityMethodSpec = MethodSpec.methodBuilder("testGet" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "/{id}\", 1L)) //update\n").
                        indent().add(".andExpect(status().isOk())\n").
                        add(".andDo(print())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.id\").value(1L)); //update\n").
                        add("// .andExpect(jsonPath(\"$$.name\").value(DEFAULT_NAME));\n").
                        unindent().build()).
                build();

        MethodSpec testGetNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testGetNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "/{id}\", $T.MAX_VALUE))\n", Long.class).
                        indent().add(".andExpect(status().isOk())).\n").
                        unindent().add("hasCause(new $T());\n", entityException).unindent().build()).
                build();

        MethodSpec testGetAllEntityMethodSpec = MethodSpec.methodBuilder("testGetAll" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "?sort=id,desc\"))\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.[*].id\").value(hasItem(1L))); //update\n").
                        add("// .andExpect(jsonPath(\"$$.[*].name\").value(hasItem(DEFAULT_NAME)));\n").
                        unindent().build()).
                build();

        ParameterizedTypeName optionalEntityTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), entityClassName);
        ParameterizedTypeName pageEntityTypeName = ParameterizedTypeName.get(ClassName.get(Page.class), entityClassName);
        MethodSpec testDeleteEntityMethodSpec = MethodSpec.methodBuilder("testDelete" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        addStatement("$T pageable = $T.any($T.class)", Pageable.class, Mockito.class, Pageable.class).
                        addStatement("int databaseSizeBeforeDelete = " + repoVarName + ".findAll(pageable).getContent().size()").
                        add("this." + restMvcVarName + ".perform(delete(\"" + baseApiUrl + "/{id}\", 1L))\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isNoContent());\n\n").
                        unindent().addStatement("$T list = " + repoVarName + ".findAll(pageable)", pageEntityTypeName).
                        add("assertThat(list.getContent()).hasSize(databaseSizeBeforeDelete - 1);\n\n").
                        add("$T test = " + repoVarName + ".findById(1L); // update\n", optionalEntityTypeName).
                        addStatement("assertThat(test.isPresent()).isFalse()").build()).
                build();

        MethodSpec testDeleteNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testDeleteNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(delete(\"" + baseApiUrl + "/{id}\", $T.MAX_VALUE))\n", Long.class).
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isNoContent())).\n").
                        unindent().add("hasCause(new $T());\n", entityException).unindent().build()).
                build();

        MethodSpec testGetAllDeletedEntityMethodSpec = MethodSpec.methodBuilder("testGetAllDeleted" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(get(\"" + baseApiUrl + "/deleted?sort=id,desc\"))\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.[*].id\").value(hasItem(1L))); //update\n").
                        add("// .andExpect(jsonPath(\"$$.[*].name\").value(hasItem(DEFAULT_NAME)));\n").
                        unindent().build()).
                build();


        MethodSpec testRecoverEntityMethodSpec = MethodSpec.methodBuilder("testRecoverDeleted" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addException(Exception.class).
                addComment("some database setup\n").
                addCode(CodeBlock.builder().
                        add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "/recover/{id}\", 1L)) // update\n").
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())\n").
                        add(".andExpect(content().contentType($T.APPLICATION_JSON_UTF8_VALUE))\n", MediaType.class).
                        add(".andExpect(jsonPath(\"$$.[*].id\").value(hasItem(1L))); //update\n").
                        add("// .andExpect(jsonPath(\"$$.[*].name\").value(hasItem(DEFAULT_NAME)));\n").
                        unindent().build()).
                build();

        MethodSpec testRecoverNonExistingEntityMethodSpec = MethodSpec.methodBuilder("testRecoverNonExistent" + entityName).
                addAnnotation(Test.class).
                addAnnotation(Transactional.class).
                addModifiers(Modifier.PUBLIC).
                addCode(CodeBlock.builder().
                        add("assertThatThrownBy(() ->\n").
                        indent().add("this." + restMvcVarName + ".perform(post(\"" + baseApiUrl + "/recover/{id}\", $T.MAX_VALUE))\n", Long.class).
                        indent().add(".andDo(print())\n").
                        add(".andExpect(status().isOk())).\n").
                        unindent().add("hasCause(new $T());\n", entityException).unindent().build()).
                build();

        ClassName securityBeanConfigClassName = ClassName.get(packageName + ".config", "SecurityBeanOverrideConfiguration");
        ClassName appClassName = ClassName.get(packageName, appMainClass);
        TypeSpec testResourceTypeSpec = TypeSpec.classBuilder(entityTestResource).
                addModifiers(Modifier.PUBLIC).
                addJavadoc("TODO: Update DTOs for relevant data and adjust tests for data accordingly.\n").
                addAnnotation(AnnotationSpec.builder(RunWith.class).
                        addMember("value", "$T.class", ClassName.get(SpringRunner.class)).
                        build()).
                addAnnotation(AnnotationSpec.builder(SpringBootTest.class)
                        .addMember("classes", "{$T.class, $T.class}", securityBeanConfigClassName, appClassName).build()).
                addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build()).
                addField(repoFieldSpec).
                addField(serviceFieldSpec).
                addField(jacksonFieldSpec).
                addField(pageableFieldSpec).
                addField(exceptionFieldSpec).
                addField(entityManagerFieldSpec).
                addField(restMvcFieldSpec).
                addMethod(setupMethodSpec).
                addMethod(testEntityCreateMethodSpec).
                addMethod(testEntityCreateInvalidParentMethodSpec).
                addMethod(tesUpdateEntityMethodSpec).
                addMethod(updateNonExistingEntityMethodSpec).
                addMethod(testGetEntityMethodSpec).
                addMethod(testGetNonExistingEntityMethodSpec).
                addMethod(testGetAllEntityMethodSpec).
                addMethod(testDeleteEntityMethodSpec).
                addMethod(testDeleteNonExistingEntityMethodSpec).
                addMethod(testGetAllDeletedEntityMethodSpec).
                addMethod(testRecoverEntityMethodSpec).
                addMethod(testRecoverNonExistingEntityMethodSpec).
                build();

        return buildJavaFile(resourceTestPackage, testResourceTypeSpec).
                toBuilder().
                addStaticImport(testUtilClassName, "createFormattingConversionService").
                addStaticImport(ClassName.get(resourceTestPackage, entityName + "DataUtil"), "*").
                addStaticImport(ClassName.get(Assertions.class), "*").
                addStaticImport(ClassName.get(Matchers.class), "hasItem").
                addStaticImport(ClassName.get(MockMvcRequestBuilders.class), "*").
                addStaticImport(ClassName.get(MockMvcResultMatchers.class), "*").
                addStaticImport(ClassName.get(MockMvcResultHandlers.class), "print").
                build();
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

    private JavaFile buildJavaFile(String domainPackage, TypeSpec jpaEntityTypeSpec) {
        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                indent("    ").
                build();
    }
}