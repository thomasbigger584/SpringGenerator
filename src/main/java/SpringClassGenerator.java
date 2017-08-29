import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.io.IOException;
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
        String projectLocation = "/Users/thomasbigger/Desktop/projects/backend/incident-logger-backend";
        String javaSourceLocation = projectLocation + "/src/main/java/";

        Log.e("Enter package name: ");
//        String packageName = scanner.next();
        String packageName = "com.pa.twb.incidentlogger";

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

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build();

        return JavaFile.builder(domainPackage, jpaEntityTypeSpec).build();
    }

    private static JavaFile createRepository(String packageName, String entityName) {

        String repositoryPackage = packageName + ".repository";
        String entityRepository = entityName + "Repository";

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .build();

        return JavaFile.builder(repositoryPackage, jpaEntityTypeSpec).build();
    }

    private static JavaFile createMapper(String packageName, String entityName) {

        String repositoryPackage = packageName + ".service.mapper";
        String entityRepository = entityName + "Mapper";

        TypeSpec jpaEntityTypeSpec = TypeSpec.interfaceBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC)
                .build();

        return JavaFile.builder(repositoryPackage, jpaEntityTypeSpec).build();
    }

    private static JavaFile createService(String packageName, String entityName) {

        String repositoryPackage = packageName + ".service";
        String entityRepository = entityName + "Service";

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build();

        return JavaFile.builder(repositoryPackage, jpaEntityTypeSpec).build();
    }

    private static JavaFile createResource(String packageName, String entityName) {

        String repositoryPackage = packageName + ".web.rest";
        String entityRepository = entityName + "Resource";

        TypeSpec jpaEntityTypeSpec = TypeSpec.classBuilder(entityRepository)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build();

        return JavaFile.builder(repositoryPackage, jpaEntityTypeSpec).build();
    }
}
