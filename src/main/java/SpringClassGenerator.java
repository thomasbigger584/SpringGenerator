import com.squareup.javapoet.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.lang.model.element.Modifier;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SpringClassGenerator {

    public static void main(String... args) {

        Scanner scanner = new Scanner(System.in);

        Log.e("Enter project location: ");
//        String projectLocation = scanner.next();
        String projectLocation = "/Users/thomasbigger/Desktop/projects/backend/fans-backend";
        String javaSourceLocation = projectLocation + "/src/main/java/";

        Log.e("Enter package name: ");
//        String packageName = scanner.next();
        String packageName = "com.pa.backend";

        Log.e("Enter entity name: ");
        String entityName = scanner.next();

        entityName = entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

        List<JavaFile> javaFiles = new ArrayList<>();

        javaFiles.add(createJpaEntity(packageName, entityName));
        javaFiles.add(createRepository(packageName, entityName));
        javaFiles.add(createMapper(packageName, entityName));
        javaFiles.add(createService(packageName, entityName));
        javaFiles.add(createResource(packageName, entityName));

        Path outputFileLocation = Paths.get(javaSourceLocation);

        try {
            for (JavaFile javaFile : javaFiles) {
                javaFile.writeTo(outputFileLocation);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JavaFile createJpaEntity(String packageName, String entityName) {

        String domainPackage = packageName + ".domain";

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityName).

                addModifiers(Modifier.PUBLIC, Modifier.FINAL).

                addSuperinterface(Serializable.class).
                addField(FieldSpec.
                        builder(TypeName.LONG, "serialVersionUID",
                                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).
                        initializer("1").
                        build()).

                addAnnotation(Entity.class).
                addAnnotation(AnnotationSpec.builder(Table.class).
                        addMember("name", "\"" + entityName.toLowerCase() + "\"").build()).
                addAnnotation(AnnotationSpec.builder(Cache.class).
                        addMember("usage", "$T.$L", CacheConcurrencyStrategy.class,
                                CacheConcurrencyStrategy.NONSTRICT_READ_WRITE.name()).
                        build()).

                build();

        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                build();
    }

    private static JavaFile createRepository(String packageName, String entityName) {

        String repositoryPackage = packageName + ".repository";
        String entityRepository = entityName + "Repository";

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(JpaRepository.class),
                        ClassName.get(packageName + ".domain", entityName), ClassName.get(Long.class)))
                .build();

        return JavaFile.builder(repositoryPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                build();
    }

    private static JavaFile createMapper(String packageName, String entityName) {

        String mapperPackage = packageName + ".service.mapper";
        String entityMapper = entityName + "Mapper";

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityMapper)
                .addModifiers(Modifier.PUBLIC)
                .build();

        return JavaFile.builder(mapperPackage, jpaEntityTypeSpec).build();
    }

    private static JavaFile createService(String packageName, String entityName) {

        String repositoryPackage = packageName + ".service";
        String entityService = entityName + "Service";

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityService)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Service.class)
                .addAnnotation(Transactional.class)
                .build();

        return JavaFile.builder(repositoryPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                build();
    }

    private static JavaFile createResource(String packageName, String entityName) {

        String repositoryPackage = packageName + ".web.rest";
        String entityRepository = entityName + "Resource";

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityRepository).
                addModifiers(Modifier.PUBLIC).
                addAnnotation(RestController.class).
                addAnnotation(AnnotationSpec.builder(RequestMapping.class).
                        addMember("value", "\"/api/" + entityName.toLowerCase() + "\"").
                        build()).
                addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).
                        addMember("value", "\"unused\"").
                        build()).
                build();

        return JavaFile.builder(repositoryPackage, jpaEntityTypeSpec).
                skipJavaLangImports(true).
                build();
    }
}
