import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesLoader {

    private static final String FOLDER_CONFIG = "config";

    private static final String CONFIG_USER = "user.properties";

    private Properties properties;

    public PropertiesLoader() {
        this.properties = new Properties();
    }

    public void loadUserConfig() {

        ClassLoader classLoader = getClass().getClassLoader();

        String userConfigPath = FOLDER_CONFIG + File.separator + CONFIG_USER;

        System.out.println(userConfigPath);
        InputStream inputStream = classLoader.getResourceAsStream(userConfigPath);

        if (inputStream != null) {

            try {

                properties.load(inputStream);

                inputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
