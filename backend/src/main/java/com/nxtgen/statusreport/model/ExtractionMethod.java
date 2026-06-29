package com.nxtgen.statusreport.model;

public enum ExtractionMethod {
    RULE,
    LLM,
    /** The verdict was corrected by a user, overriding the rule/LLM result. */
    MANUAL
}
