package models;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents an email object containing details such as sender, recipients, subject, body, flags,
 * and attachments. This class provides methods for constructing an email, modifying its properties,
 * and managing its metadata.
 */
public class Email {
    private int messageNumber;
    private String messageId;
    private long uid = 0;
    private String from;
    private List<String> to;
    private List<String> cc;
    private String subject;
    private String body;
    private String bodyHtml;
    private boolean isHtml;
    private Date date;
    private List<String> flags;
    private int size;
    private List<File> attachments;

    public Email() {
        this.to = new ArrayList<>();
        this.cc = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.attachments = new ArrayList<>();
    }

    public Email(String from, String to, String subject, String body) {
        this();
        this.from = from;
        this.to.add(to);
        this.subject = subject;
        this.body = body;
        this.date = new Date();
    }

    // Getters and Setters
    public int getMessageNumber() {
        return messageNumber;
    }

    public void setMessageNumber(int messageNumber) {
        this.messageNumber = messageNumber;
    }

    public String getMessageId() {
        return messageId;
    }

    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<String> getTo() {
        return to;
    }

    public void setTo(List<String> to) {
        this.to = to;
    }

    public void addTo(String email) {
        this.to.add(email);
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public void addCc(String email) {
        this.cc.add(email);
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public boolean isHtml() {
        return isHtml;
    }

    public void setHtml(boolean html) {
        isHtml = html;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public List<String> getFlags() {
        return flags;
    }

    public void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public void addFlag(String flag) {
        if (!this.flags.contains(flag)) {
            this.flags.add(flag);
        }
    }

    public boolean hasFlag(String flag) {
        return this.flags.contains(flag);
    }

    public void removeFlag(String flag) {
        this.flags.remove(flag);
    }

    public void toggleFlag(String flag) {
        if (hasFlag(flag)) {
            removeFlag(flag);
        } else {
            addFlag(flag);
        }
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<File> getAttachments() {
        return attachments;
    }

    public void addAttachment(File file) {
        attachments.add(file);
    }

    public void setAttachments(List<File> attachments) {
        this.attachments = attachments;
    }


    @Override
    public String toString() {
        return String.format("Email{from='%s', to=%s, subject='%s', date=%s, flags=%s}",
                from, to, subject, date, flags);
    }
}