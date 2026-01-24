package com.codesphere.backend.dto;

public class ExecutionResult {

    private String output;
    private String error;
    private String status;

    public ExecutionResult(String output, String error, String status) {
        this.output = output;
        this.error = error;
        this.status = status;
    }

    public String getOutput() { return output; }
    public String getError() { return error; }
    public String getStatus() { return status; }
}
