package com.nxtgen.statusreport.service.extraction;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentTextExtractor {

    /** @return true if this extractor handles the given original filename. */
    boolean supports(String originalFilename);

    /** Pulls plain text out of the document, preserving paragraph breaks. */
    String extract(InputStream inputStream) throws IOException;
}
