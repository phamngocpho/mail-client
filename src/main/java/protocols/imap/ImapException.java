package protocols.imap;

public class ImapException extends Exception {
    private String command;
    private String response;

    public ImapException(String message) {
        super(message);
    }

    public ImapException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImapException(String command, String response, String message) {
        super(message);
        this.command = command;
        this.response = response;
    }

    public String getCommand() {
        return command;
    }

    public String getResponse() {
        return response;
    }

    @Override
    public String toString() {
        if (command != null && response != null) {
            return String.format("ImapException: %s\nCommand: %s\nResponse: %s",
                    getMessage(), command, response);
        }
        return super.toString();
    }
}