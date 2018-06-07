import com.beust.jcommander.IStringConverter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathConverter implements IStringConverter<Path> {
    @Override
    public Path convert(String value) {
        return Paths.get(value);
    }
}