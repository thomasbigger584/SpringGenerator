import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.squareup.javapoet.*;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.lang.model.element.Modifier;
import javax.validation.Valid;
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
    private Path javaSrcPath = Paths.get("/Users/thomasbigger/Desktop/projects/backend/skylark-backend/src/main/java/");

    @Parameter(names = "--ep", description = "Extension Prefix for generated files")
    private String extensionPrefix = "Ext";

    @Parameter(names = "--pn", description = "Package name for generated files")
    private String packageName = "com.pa.twb";

    @Parameter(names = "--es", description = "Supports elastic search")
    private boolean supportsElasticSearch = true;

    @Parameter(names = "-d", description = "Turn on debugging")
    private boolean isDebug = false;

    public static void main(String... args) {
        SpringClassGenerator scg = new SpringClassGenerator();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(scg)
                .build();
        jCommander.parse(args);

        if (scg.isDebug) {
            scg.entities.add("AssetCategory");
        }

        scg.generate();
    }

    private void generate() {

        for (String entityName : entities) {

            entityName = entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

            List<JavaFile> javaFiles = new ArrayList<>();

            javaFiles.add(createRepository(entityName));
            javaFiles.add(createDto("Get", entityName));
            javaFiles.add(createDto("Create", entityName));
            javaFiles.add(createMapper(entityName));
            if (supportsElasticSearch) {
                javaFiles.add(createSearchRespository(entityName));
            }
            javaFiles.add(createService(entityName));
            javaFiles.add(createResource(entityName));

            try {
                for (JavaFile javaFile : javaFiles) {
                    javaFile.writeTo(javaSrcPath);
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

    private JavaFile createDto(String dtoPrefix, String entityName) {

        String dtoPackage = packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase();
        String entityDto = dtoPrefix + entityName + "DTO";

        TypeSpec dtoTypeSpec = TypeSpec.classBuilder(entityDto)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build())
                .build();

        return buildJavaFile(dtoPackage, dtoTypeSpec);
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
                addParameter(createDtoClassName, "create" + entityName.toLowerCase() + "Dto").
                build();

        ClassName getDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Get" + entityName + "DTO");

        String entityVarName = entityName.substring(0, 1).toLowerCase() + entityName.substring(1);
        MethodSpec getToEntityMethod = MethodSpec.methodBuilder("entityToGetDto").
                addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).
                returns(getDtoClassName).
                addParameter(entityClassName, entityVarName).
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
        String createDtoVarName = "create" + entityName.toLowerCase() + "Dto";

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

        ParameterizedTypeName optionalEntityTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), entityClassName);
        MethodSpec findMethodSpec = MethodSpec.methodBuilder("getById").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(optionalEntityTypeName).
                addParameter(Long.class, "id").
                addStatement("return $T.ofNullable(findOne(id))", Optional.class).
                build();

        ParameterizedTypeName optionalDtoTypeName = ParameterizedTypeName.get(ClassName.get(Optional.class), getDtoClassName);
        MethodSpec findDtoMethodSpec = MethodSpec.methodBuilder("getDtoById").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(optionalDtoTypeName).
                addParameter(Long.class, "id").
                addStatement("return getById(id).map($N::entityToGetDto)", mapperVarName).
                build();

        ParameterizedTypeName pagedDtoTypeName = ParameterizedTypeName.get(ClassName.get(Page.class), getDtoClassName);
        MethodSpec pagedDtoMethodSpec = MethodSpec.methodBuilder("getAllDto").
                addModifiers(Modifier.PUBLIC).
                addAnnotation(AnnotationSpec.builder(Transactional.class).
                        addMember("readOnly", "true").
                        build()).
                returns(pagedDtoTypeName).
                addParameter(Pageable.class, "pageable").
                addStatement("return findAll(pageable).map($N::entityToGetDto)", mapperVarName).
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
                addMethod(findMethodSpec).
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
        ClassName getDtoClassName =
                ClassName.get(packageName + ".service." + extensionPrefix.toLowerCase() + ".dto." + entityName.toLowerCase(),
                        "Get" + entityName + "DTO");
        ParameterizedTypeName getResponseEntityTypeName = ParameterizedTypeName.get(ClassName.get(ResponseEntity.class), getDtoClassName);

        ClassName headerUtilClassName = ClassName.get(packageName + ".web.rest.util", "HeaderUtil");

        MethodSpec createMethodSpec = MethodSpec.methodBuilder("create" + entityName).
                addAnnotation(AnnotationSpec.builder(PostMapping.class).
                        addMember("value", "\"/create\"").
                        build()).
                addParameter(ParameterSpec.builder(createDtoClassName, createDtoVarName).addAnnotation(Valid.class).build()).
                addStatement("$T result = $N.create($N)", getDtoClassName, serviceVarName, createDtoVarName).
                addStatement("return $T.status($T.CREATED)\n.headers($T.createEntityCreationAlert(ENTITY_NAME, \"1\"))\n.body(result)", ResponseEntity.class, HttpStatus.class, headerUtilClassName).
                returns(getResponseEntityTypeName).
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
                addStatement("$T result = $N.getDtoById(id)", optionalDtoTypeName, serviceVarName).
                addStatement("return $T.wrapOrNotFound(result)", responseUtilClassName).
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
                addMethod(getByIdMethodSpec).
                build();

        return buildJavaFile(repositoryPackage, jpaEntityTypeSpec);
    }

    private JavaFile buildJavaFile(String domainPackage, TypeSpec jpaEntityTypeSpec) {
        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                indent("\t").
                build();
    }
}
