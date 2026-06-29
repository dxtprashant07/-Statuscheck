package com.nxtgen.statusreport.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootDir;

    public FileStorageService(@Value("${app.storage.root-dir}") String rootDir) throws IOException {
        this.rootDir = Path.of(rootDir);
        Files.createDirectories(this.rootDir);
    }

    /** Stores the file under a random name to avoid collisions and path traversal, returns the path on disk. */
    public String store(MultipartFile file) throws IOException {
        String safeSuffix = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                : "";
        String storedName = UUID.randomUUID() + safeSuffix;
        Path target = rootDir.resolve(storedName);
        file.transferTo(target);
        return target.toString();
    }
}
