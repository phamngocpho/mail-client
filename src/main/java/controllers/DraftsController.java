package controllers;

import models.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.PreferencesUtils;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Controller quản lý drafts
 * Lưu trữ drafts vào file system để persist among các session
 */
public class DraftsController {
    private static DraftsController instance;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private static final Logger logger = LoggerFactory.getLogger(DraftsController.class);

    private final Path draftsDir;
    private final List<Email> drafts = new ArrayList<>();
    private int draftIdCounter = 0;

    private DraftsController() {
        // Initialize drafts directory
        Path baseDir = getDraftsDirectory();
        this.draftsDir = baseDir.resolve("drafts");

        try {
            Files.createDirectories(draftsDir);
            logger.info("Drafts directory: {}", draftsDir);
            loadAllDrafts();
        } catch (IOException e) {
            logger.error("Failed to create drafts directory", e);
        }
    }

    public static DraftsController getInstance() {
        if (instance == null) {
            instance = new DraftsController();
        }
        return instance;
    }

    /**
     * Xác định thư mục lưu drafts
     */
    private Path getDraftsDirectory() {
        try {
            Path classPath = Paths.get(DraftsController.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            Path baseDir;
            if (classPath.toString().contains("target" + File.separator + "classes")) {
                baseDir = classPath.getParent().getParent();
            } else if (classPath.toString().endsWith(".jar")) {
                baseDir = classPath.getParent();
            } else {
                baseDir = Paths.get(System.getProperty("user.dir"), "Mail Client");
            }

            return baseDir.resolve(".mailclient/drafts");
        } catch (Exception e) {
            logger.error("Error determining drafts directory", e);
            return Paths.get(System.getProperty("user.dir"), "Mail Client", "drafts");
        }
    }

    /**
     * Lưu email vào drafts
     */
    public boolean saveDraft(Email email) {
        try {
            // Assign ID if new draft
            if (email.getMessageId() == null || email.getMessageId().isEmpty()) {
                email.setMessageId("draft_" + (++draftIdCounter) + "_" + System.currentTimeMillis());
            }

            // Set date if not set
            if (email.getDate() == null) {
                email.setDate(new Date());
            }

            // Save to file
            File draftFile = draftsDir.resolve(email.getMessageId() + ".draft").toFile();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(draftFile))) {
                oos.writeObject(email);
            }

            // Update memory list
            drafts.removeIf(d -> d.getMessageId().equals(email.getMessageId()));
            drafts.add(email);

            logger.info("Draft saved: {}", email.getMessageId());
            pcs.firePropertyChange("drafts", null, drafts);

            return true;
        } catch (Exception e) {
            logger.error("Failed to save draft", e);
            return false;
        }
    }

    /**
     * Load tất cả drafts từ file system
     */
    private void loadAllDrafts() {
        drafts.clear();

        File[] files = draftsDir.toFile().listFiles((dir, name) -> name.endsWith(".draft"));
        if (files == null) return;

        for (File file : files) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Email draft = (Email) ois.readObject();
                drafts.add(draft);

                // Update counter
                if (draft.getMessageId() != null && draft.getMessageId().startsWith("draft_")) {
                    String[] parts = draft.getMessageId().split("_");
                    if (parts.length > 1) {
                        try {
                            int id = Integer.parseInt(parts[1]);
                            draftIdCounter = Math.max(draftIdCounter, id);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to load draft: {}", file.getName(), e);
            }
        }

        logger.info("Loaded {} drafts", drafts.size());
    }

    /**
     * Lấy tất cả drafts
     */
    public List<Email> getAllDrafts() {
        return new ArrayList<>(drafts);
    }

    /**
     * Xóa draft
     */
    public boolean deleteDraft(Email draft) {
        try {
            // Delete file
            File draftFile = draftsDir.resolve(draft.getMessageId() + ".draft").toFile();
            if (draftFile.exists()) {
                draftFile.delete();
            }

            // Remove from memory
            drafts.removeIf(d -> d.getMessageId().equals(draft.getMessageId()));

            logger.info("Draft deleted: {}", draft.getMessageId());
            pcs.firePropertyChange("drafts", null, drafts);

            return true;
        } catch (Exception e) {
            logger.error("Failed to delete draft", e);
            return false;
        }
    }

    /**
     * Xóa draft sau khi gửi thành công
     */
    public void deleteDraftAfterSend(String draftId) {
        drafts.stream()
                .filter(d -> d.getMessageId().equals(draftId))
                .findFirst()
                .ifPresent(this::deleteDraft);
    }

    /**
     * Đếm số lượng drafts
     */
    public int getDraftsCount() {
        return drafts.size();
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
