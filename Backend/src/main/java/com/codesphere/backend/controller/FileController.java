package com.codesphere.backend.controller;

import com.codesphere.backend.dto.ApiResponse;
import com.codesphere.backend.dto.FileRequest;
import com.codesphere.backend.entity.FileEntity;
import com.codesphere.backend.entity.ProjectEntity;
import com.codesphere.backend.repository.FileRepository;
import com.codesphere.backend.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectName}/files")
public class FileController {

    private static final String BASE_DIR = "codesphere_workspace";

    private final ProjectRepository projectRepository;
    private final FileRepository fileRepository;

    public FileController(ProjectRepository projectRepository,
                          FileRepository fileRepository) {
        this.projectRepository = projectRepository;
        this.fileRepository = fileRepository;
    }

    private String detectLanguage(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx == -1 ? "unknown" : filename.substring(idx + 1);
    }

    // 1️⃣ CREATE FILE
    @PostMapping
    public ResponseEntity<ApiResponse<String>> createFile(
            @PathVariable String projectName,
            @RequestBody FileRequest request) throws IOException {

        ProjectEntity project = projectRepository.findByName(projectName)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (fileRepository.existsByFilenameAndProjectId(
                request.getFilename(), project.getId())) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "File already exists", null));
        }

        // FS
        Path projectPath = Path.of(BASE_DIR, projectName);
        Path filePath = projectPath.resolve(request.getFilename());
        Files.createFile(filePath);

        // DB
        FileEntity file = new FileEntity();
        file.setFilename(request.getFilename());
        file.setLanguage(detectLanguage(request.getFilename()));
        file.setProject(project);
        fileRepository.save(file);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "File created", request.getFilename())
        );
    }

    // 2️⃣ LIST FILES (DB-based)
    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> listFiles(
            @PathVariable String projectName) {

        ProjectEntity project = projectRepository.findByName(projectName)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        List<String> files = fileRepository.findByProjectId(project.getId())
                .stream()
                .map(FileEntity::getFilename)
                .toList();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Files fetched", files)
        );
    }
    


 // 3️⃣ READ FILE
     @GetMapping("/{filename}")
     public ResponseEntity<ApiResponse<String>> readFile(
             @PathVariable String projectName,
             @PathVariable String filename) throws IOException {

        ProjectEntity project = projectRepository.findByName(projectName)
        		.orElseThrow(() -> new RuntimeException("Project Not Found"));
        
        FileEntity file = fileRepository.findByProjectId(project.getId())
        		.stream()
        		.filter(f -> f.getFilename().equals(filename))
        		.findFirst()
        		.orElseThrow(()-> new RuntimeException("File Not Found"));
        
        Path filePath = Path.of(BASE_DIR, projectName, filename);

         if (!Files.exists(filePath)) {
             return ResponseEntity.badRequest()
                     .body(new ApiResponse<>(false, "File not found", null));
         }

         String content = Files.readString(filePath);

         return ResponseEntity.ok(
                 new ApiResponse<>(true, "File read", content)
         );
     }


	 // 4️⃣ SAVE / UPDATE FILE
     @PutMapping("/{filename}")
     public ResponseEntity<ApiResponse<Void>> saveFile(
             @PathVariable String projectName,
             @PathVariable String filename,
             @RequestBody String content) throws IOException {

    	 ProjectEntity project = projectRepository.findByName(projectName)
    			 .orElseThrow(() -> new RuntimeException("Project Not Found"));
    	 
    	 FileEntity file = fileRepository.findByProjectId(project.getId())
    			 .stream()
    			 .filter(f -> f.getFilename().equals(filename))
    			 .findFirst()
    			 .orElseThrow(() -> new RuntimeException("File not Found"));
    	 
    	 Path filePath = Path.of(BASE_DIR, projectName, filename);

         Files.writeString(filePath, content);

         return ResponseEntity.ok(
                 new ApiResponse<>(true, "File saved", null)
         );
     }

    // 3️⃣ DELETE FILE (DB + FS)
    @DeleteMapping("/{filename}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable String projectName,
            @PathVariable String filename) throws IOException {

        ProjectEntity project = projectRepository.findByName(projectName)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        FileEntity file = fileRepository.findByProjectId(project.getId())
                .stream()
                .filter(f -> f.getFilename().equals(filename))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("File not found"));

        // FS
        Path filePath = Path.of(BASE_DIR, projectName, filename);
        Files.deleteIfExists(filePath);

        // DB
        fileRepository.delete(file);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "File deleted", null)
        );
    }
}


