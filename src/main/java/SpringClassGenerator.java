import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.squareup.javapoet.*;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.lang.model.element.Modifier;
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

    @Parameter(names = "-e", description = "Entity to parse")
    private List<String> entities = new ArrayList<>();

    @Parameter(names = "-p", converter = PathConverter.class, description = "Path to Java Src package")
    private Path javaSrcPath = Paths.get("/Users/thomasbigger/Desktop/projects/backend/leep-platform/leepcore/src/main/java/");

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

            List<JavaFile> javaFiles = new ArrayList<>();

            javaFiles.add(createRepository(entityName));
            javaFiles.add(createDto("Get", entityName, true));
            javaFiles.add(createDto("Create", entityName, false));
            javaFiles.add(createDto("Update", entityName, true));
            javaFiles.add(createMapper(entityName));
            if (supportsElasticSearch) {
                javaFiles.add(createSearchRespository(entityName));
            }
            javaFiles.add(createException(entityName));
            javaFiles.add(createService(entityName));
            javaFiles.add(createResource(entityName));

            try {
                for (JavaFile javaFile : javaFiles) {
                    javaFile.writeTo(javaSrcPath);
                }
//              TODO: look at formatting these files
//              TODO: https://www.jetbrains.com/help/idea/command-line-formatter.html
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

        MethodSpec updateEntityMethod = MethodSpec.methodBuilder("entityToEntityUpdate").
                addModifiers(Modifier.PUBLIC, Modifier.DEFAULT).
                addParameter(entityClassName, entityName).
                addParameter(updateDtoClassName, "update" + entityName + "Dto").
                build();

        TypeSpec mapperTypeSpec = TypeSpec.interfaceBuilder(entityMapper)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Mapper.class).
                        addMember("componentModel", "\"spring\"").
                        addMember("uses", "{,}").
                        build())
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build())
                .addMethod(createToEntityMethod)
                .addMethod(getToEntityMethod)
                .addMethod(updateEntityMethod)
                .build();

        return buildJavaFile(mapperPackage, mapperTypeSpec);
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
                addParameter(repositoryClassName, repositoryVarName);

        String superStatement = "super(%s)";
        String parameters = repositoryVarName + ((supportsElasticSearch) ? ", " + repositorySearchVarName : "");
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

        ParameterizedTypeName optionalDtoTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), getDtoClassName);
        ParameterizedTypeName optionalEntityTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), entityClassName);

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
                        .addStatement("$T result = findOne($N.getId())", optionalEntityTypeName, updateDtoVarName)
                        .add(CodeBlock.builder().beginControlFlow("if (result.isPresent())").
                                addStatement("$T $N = result.get()", entityClassName, entityVarName).
                                addStatement("$N.entityToEntityUpdate($N, $N)", mapperVarName, entityVarName, updateDtoVarName).
                                addStatement("return $N.entityToGetDto($N)", mapperVarName, entityVarName).
                                endControlFlow().
                                addStatement("throw new $T()", entityException).
                                build())
                        .build()).build();

        MethodSpec findDtoMethodSpec = MethodSpec.methodBuilder("getById").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(optionalDtoTypeName).
                addParameter(Long.class, "id").
                addStatement("$T result = findOne(id)", optionalEntityTypeName).
                addStatement("return result.map($N::entityToGetDto)", mapperVarName).
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
                addStatement("$T page = findAll(pageable)", pagedEntityTypeName).
                addStatement("return page.map($N::entityToGetDto)", mapperVarName).
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
                addMethod(findDtoMethodSpec).
                addMethod(pagedDtoMethodSpec);

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

        String resourceUriName = "";
        String[] words = entityName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
        for (int index = 0; index < words.length; index++) {
            resourceUriName = resourceUriName.concat(words[index]);
            if (index < words.length - 1) {
                resourceUriName = resourceUriName.concat("-");
            }
        }

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
                addStatement("$N.delete(id)", serviceVarName).
                addStatement("return $T.status($T.OK)\n.headers($T.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build()", ResponseEntity.class, HttpStatus.class, headerUtilClassName).
                returns(responseVoidTypeName).
                addModifiers(Modifier.PUBLIC).
                build();

        ParameterizedTypeName optionalDtoTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), getDtoClassName);
        ClassName responseUtilClassName = ClassName.get("io.github.jhipster.web.util", "ResponseUtil");
        MethodSpec getByIdMethodSpec = MethodSpec.methodBuilder("get" + entityName + "ById").
                addAnnotation(AnnotationSpec.builder(GetMapping.class).
                        addMember("value", "\"/{id}\"").
                        build()).
                addParameter(ParameterSpec.builder(Long.class, "id").
                        addAnnotation(AnnotationSpec.builder(PathVariable.class).
                                addMember("value", "\"id\"").
                                build()).build()).
                addStatement("$T result = $N.getById(id)", optionalDtoTypeName, serviceVarName).
                addStatement("return $T.wrapOrNotFound(result)", responseUtilClassName).
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
                        HttpHeaders.class, paginationUtilClassName, resourceUriName.toLowerCase()).
                addStatement("return new $T<>(page.getContent(), headers, $T.OK)", ResponseEntity.class, HttpStatus.class).
                returns(responseDtoTypeName).
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
                addMethod(deleteMethodSpec).
                addMethod(getByIdMethodSpec).
                addMethod(getAllDtoMethodSpec).
                build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private JavaFile buildJavaFile(String domainPackage, TypeSpec jpaEntityTypeSpec) {
        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                indent("    ").
                build();
    }
}