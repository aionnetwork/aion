package org.aion.api.server.types;

public class SolcOutput {

    public String errors;
    public String output;

    public SolcOutput(String errors, String output) {
        this.errors = errors;
        this.output = output;
    }
}
