package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý cache email body và attachments trên disk
 * Lưu metadata dưới dạng JSON để persist giữa các lần chạy app
 */
public class EmailCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(EmailCacheManager.class);
    private static final String CACHE_DIR = ".mailclient/cache";
    private static final String BODY_CACHE_FILE = "email_bodies.json";
    private static final String HTML_CACHE_FILE = "email_html.json";
    private static final String ATTACHMENTS_CACHE_FILE = "email_attachments.json";
    
    private final Path cacheDir;
    private final Gson gson;
    
    // In-memory cache
    private Map<Integer, String> bodyCache;
    private Map<Integer, String> bodyHtmlCache;
    private Map<Integer, List<String>> attachmentsCache; // Lưu đường dẫn file, không phải File object
    
    public EmailCacheManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Xác định cache directory
        try {
            Path classPath = Paths.get(EmailCacheManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            
            Path baseDir;
            if (classPath.toString().contains("target" + File.separator + "classes")) {
                baseDir = classPath.getParent().getParent();
            } else if (classPath.toString().endsWith(".jar")) {
                baseDir = classPath.getParent();
            } else {
                baseDir = Paths.get(System.getProperty("user.dir"), "Mail Client");
            }
            
            this.cacheDir = baseDir.resolve(CACHE_DIR);
            
            // Tạo thư mục cache nếu chưa tồn tại
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                logger.info("Created cache directory: {}", cacheDir);
            }
            
            // Load cache từ disk
            loadFromDisk();
            
        } catch (Exception e) {
            logger.error("Error initializing cache manager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize cache", e);
        }
    }
    
    /**
     * Load cache từ disk khi khởi động app
     */
    private void loadFromDisk() {
        bodyCache = loadMapFromFile(BODY_CACHE_FILE, new TypeToken<Map<Integer, String>>(){}.getType());
        bodyHtmlCache = loadMapFromFile(HTML_CACHE_FILE, new TypeToken<Map<Integer, String>>(){}.getType());
        attachmentsCache = loadMapFromFile(ATTACHMENTS_CACHE_FILE, 
                new TypeToken<Map<Integer, List<String>>>(){}.getType());
        
        logger.info("Loaded cache from disk: {} bodies, {} HTML, {} attachments",
                bodyCache.size(), bodyHtmlCache.size(), attachmentsCache.size());
    }
    
    /**
     * Load map từ file JSON
     */
    private <T> T loadMapFromFile(String filename, Type type) {
        Path file = cacheDir.resolve(filename);
        
        if (!Files.exists(file)) {
            logger.debug("Cache file not found: {}", filename);
            return createEmptyMap(type);
        }
        
        try (Reader reader = Files.newBufferedReader(file)) {
            T map = gson.fromJson(reader, type);
            return map != null ? map : createEmptyMap(type);
        } catch (Exception e) {
            logger.warn("Failed to load cache from {}: {}", filename, e.getMessage());
            return createEmptyMap(type);
        }
    }
    
    /**
     * Tạo empty map phù hợp với type
     */
    @SuppressWarnings("unchecked")
    private <T> T createEmptyMap(Type type) {
        if (type.toString().contains("List<String>")) {
            return (T) new HashMap<Integer, List<String>>();
        }
        return (T) new HashMap<Integer, String>();
    }
    
    /**
     * Lưu cache xuống disk
     */
    private void saveToDisk() {
        saveMapToFile(BODY_CACHE_FILE, bodyCache);
        saveMapToFile(HTML_CACHE_FILE, bodyHtmlCache);
        saveMapToFile(ATTACHMENTS_CACHE_FILE, attachmentsCache);
        
        logger.debug("Saved cache to disk");
    }
    
    /**
     * Lưu map vào file JSON
     */
    private void saveMapToFile(String filename, Object map) {
        Path file = cacheDir.resolve(filename);
        
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(map, writer);
        } catch (Exception e) {
            logger.error("Failed to save cache to {}: {}", filename, e.getMessage());
        }
    }
    
    /**
     * Kiểm tra xem email body có trong cache không
     */
    public boolean hasBody(int messageNumber) {
        return bodyCache.containsKey(messageNumber);
    }
    
    /**
     * Lấy body từ cache
     */
    public String getBody(int messageNumber) {
        return bodyCache.get(messageNumber);
    }
    
    /**
     * Lấy HTML body từ cache
     */
    public String getBodyHtml(int messageNumber) {
        return bodyHtmlCache.get(messageNumber);
    }
    
    /**
     * Lấy attachments từ cache (trả về List<File>)
     * KHÔNG filter file không tồn tại - để caller tự check
     */
    public List<File> getAttachments(int messageNumber) {
        List<String> paths = attachmentsCache.get(messageNumber);
        if (paths == null) return null;
        
        List<File> files = new ArrayList<>();
        for (String path : paths) {
            files.add(new File(path));
        }
        return files;
    }
    
    /**
     * Kiểm tra xem có attachments cached không
     */
    public boolean hasAttachments(int messageNumber) {
        List<String> paths = attachmentsCache.get(messageNumber);
        return paths != null && !paths.isEmpty();
    }
    
    /**
     * Cache email body và HTML
     */
    public void cacheBody(int messageNumber, String body, String bodyHtml) {
        bodyCache.put(messageNumber, body != null ? body : "");
        bodyHtmlCache.put(messageNumber, bodyHtml != null ? bodyHtml : "");
        saveToDisk();
        logger.debug("Cached body for message #{}", messageNumber);
    }
    
    /**
     * Cache attachments
     */
    public void cacheAttachments(int messageNumber, List<File> attachments) {
        List<String> paths = new ArrayList<>();
        for (File file : attachments) {
            paths.add(file.getAbsolutePath());
        }
        attachmentsCache.put(messageNumber, paths);
        saveToDisk();
        logger.debug("Cached {} attachments for message #{}", paths.size(), messageNumber);
    }
    
    /**
     * Clear toàn bộ cache
     */
    public void clearAll() {
        bodyCache.clear();
        bodyHtmlCache.clear();
        attachmentsCache.clear();
        saveToDisk();
        logger.info("Cleared all email cache");
    }
    
    /**
     * Clear cache cho một message cụ thể
     */
    public void clearMessage(int messageNumber) {
        bodyCache.remove(messageNumber);
        bodyHtmlCache.remove(messageNumber);
        attachmentsCache.remove(messageNumber);
        saveToDisk();
        logger.debug("Cleared cache for message #{}", messageNumber);
    }
    
    /**
     * Lấy thông tin cache stats
     */
    public String getCacheStats() {
        return String.format("Cache stats: %d bodies, %d HTML, %d attachments",
                bodyCache.size(), bodyHtmlCache.size(), attachmentsCache.size());
    }
}

