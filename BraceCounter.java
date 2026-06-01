import java.nio.file.Files;
import java.nio.file.Paths;

public class BraceCounter {
    public static void main(String[] args) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get("d:/Gaurav/RetailOne-Multi-tenant-main/app/src/main/java/com/retailone/pos/utils/PrinterUtil.java")));
        int depth = 0;
        int line = 1;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '\n') {
                if (line >= 2760 && line <= 2800) {
                    System.out.println(line + " [depth=" + depth + "]: " + getLineText(content, line));
                }
                line++;
                inLineComment = false;
            }

            if (inLineComment) continue;

            if (inBlockComment) {
                if (c == '*' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }

            if (inChar) {
                if (c == '\\') i++;
                else if (c == '\'') inChar = false;
                continue;
            }

            if (c == '/' && i + 1 < content.length()) {
                if (content.charAt(i + 1) == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                } else if (content.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '"') inString = true;
            else if (c == '\'') inChar = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth <= 0) {
                    System.out.println("Depth became <= 0 (" + depth + ") at line " + line);
                }
            }
        }
        System.out.println("Final depth: " + depth);
    }

    private static String getLineText(String content, int targetLine) {
        String[] lines = content.split("\n");
        if (targetLine >= 1 && targetLine <= lines.length) {
            return lines[targetLine - 1].trim();
        }
        return "";
    }
}
