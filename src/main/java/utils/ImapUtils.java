package utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for IMAP protocol-specific operations.
 * Contains helper methods for building IMAP commands, handling sequences,
 * and character encoding detection.
 */
public class ImapUtils {
    
    /**
     * Build IMAP sequence-set từ list message numbers
     * Tối ưu: group consecutive numbers thành ranges (1:5 thay vì 1,2,3,4,5)
     * <p>
     * Example: [1,2,3,5,7,8,9] -> "1:3,5,7:9"
     * 
     * @param numbers danh sách message numbers
     * @return sequence-set string (ví dụ: "1:5,10,15:20")
     */
    public static String buildSequenceSet(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return "";
        }
        
        // Sort numbers
        List<Integer> sorted = new ArrayList<>(numbers);
        sorted.sort(Integer::compareTo);
        
        return buildSequenceSetFromSorted(sorted);
    }
    
    /**
     * Helper method to build sequence-set from sorted list
     */
    private static String buildSequenceSetFromSorted(List<Integer> sorted) {
        StringBuilder result = new StringBuilder();
        
        int i = 0;
        while (i < sorted.size()) {
            int rangeStart = sorted.get(i);
            int rangeEnd = rangeStart;
            
            // Find end of consecutive range
            while (i + 1 < sorted.size() && sorted.get(i + 1) == rangeEnd + 1) {
                i++;
                rangeEnd = sorted.get(i);
            }
            
            // Append range
            if (!result.isEmpty()) {
                result.append(",");
            }
            appendRange(result, rangeStart, rangeEnd);
            
            i++;
        }
        
        return result.toString();
    }
    
    /**
     * Append range to StringBuilder in IMAP format
     * Single number: "5"
     * Range: "5:10"
     */
    private static void appendRange(StringBuilder sb, int start, int end) {
        if (start == end) {
            sb.append(start);
        } else {
            sb.append(start).append(":").append(end);
        }
    }
    
    /**
     * Quote string for IMAP command
     * Surrounds the given text with double quotes and escapes special characters.
     * Luôn quote để tránh lỗi với space và ký tự đặc biệt (tiếng Việt)
     *
     * @param text the input string to be quoted
     * @return the quoted string safe for IMAP commands
     */
    public static String quoteImapString(String text) {
        if (text == null) {
            return "\"\"";
        }
        
        // Escape backslashes trước, rồi mới escape double quotes
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        
        // Luôn quote để tránh lỗi với space và ký tự đặc biệt
        return "\"" + escaped + "\"";
    }
    
    /**
     * Kiểm tra xem text có cần UTF-8 encoding không
     * (có ký tự non-ASCII như tiếng Việt, Unicode, etc.)
     * 
     * @param text text cần kiểm tra
     * @return true nếu cần UTF-8, false nếu chỉ ASCII
     */
    public static boolean needsUtf8Encoding(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // Kiểm tra nếu có ký tự non-ASCII (> 127)
        for (char c : text.toCharArray()) {
            if (c > 127) {
                return true;
            }
        }
        return false;
    }
}

