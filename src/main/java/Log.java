public class Log {

    public static void print(String pattern, Object... format) {

        if (pattern == null) {
            return;
        }

        if (format == null || format.length == 0) {
            System.err.print(pattern);
        } else {
            System.err.printf(pattern, format);
        }
    }

    public static void e(String pattern, Object... format) {
        print(pattern, format);
    }
}
