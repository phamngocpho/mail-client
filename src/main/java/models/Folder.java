package models;

/**
 * Represents a folder that can contain messages. This class includes details
 * about the folder's name, path, and message statistics, as well as its
 * availability for selection.
 */
public class Folder {
    private String name;
    private String fullPath;
    private int messageCount;
    private int unreadCount;
    private boolean selectable;

    public Folder(String name) {
        this.name = name;
        this.fullPath = name;
        this.selectable = true;
    }

    public Folder(String name, String fullPath) {
        this.name = name;
        this.fullPath = fullPath;
        this.selectable = true;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    @Override
    public String toString() {
        return String.format("Folder{name='%s', messages=%d, unread=%d}",
                name, messageCount, unreadCount);
    }
}