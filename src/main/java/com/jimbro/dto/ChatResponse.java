package com.jimbro.dto;

import java.util.List;

public class ChatResponse {
    private String message;
    private List<String> actionsTaken;
    private Object rawActions;

    public ChatResponse() {}

    public ChatResponse(String message, List<String> actionsTaken, Object rawActions) {
        this.message = message;
        this.actionsTaken = actionsTaken;
        this.rawActions = rawActions;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getActionsTaken() { return actionsTaken; }
    public void setActionsTaken(List<String> actionsTaken) { this.actionsTaken = actionsTaken; }

    public Object getRawActions() { return rawActions; }
    public void setRawActions(Object rawActions) { this.rawActions = rawActions; }
}
