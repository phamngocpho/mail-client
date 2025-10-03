package protocols.smtp;

public class SmtpException extends Exception {
    private int errorCode;
    private String command;
    private String response;

    public SmtpException(String message) {
        super(message);
    }

    public SmtpException(String message, Throwable cause) {
        super(message, cause);
    }

    public SmtpException(int errorCode, String response, String message) {
        super(message);
        this.errorCode = errorCode;
        this.response = response;
    }

    public SmtpException(String command, String response, String message) {
        super(message);
        this.command = command;
        this.response = response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getCommand() {
        return command;
    }

    public String getResponse() {
        return response;
    }

    @Override
    public String toString() {
        if (errorCode > 0) {
            return String.format("SmtpException [%d]: %s\nResponse: %s",
                    errorCode, getMessage(), response);
        }
        if (command != null && response != null) {
            return String.format("SmtpException: %s\nCommand: %s\nResponse: %s",
                    getMessage(), command, response);
        }
        return super.toString();
    }
}