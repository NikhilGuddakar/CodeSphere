package com.codesphere.backend.controller;

import com.codesphere.backend.dto.ApiResponse;
import com.codesphere.backend.dto.ProjectRequest;
import com.codesphere.backend.entity.ProjectEntity;
import com.codesphere.backend.entity.UserEntity;
import com.codesphere.backend.repository.ProjectRepository;
import com.codesphere.backend.repository.UserRepository;



import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private static final String BASE_DIR =
	        System.getProperty("user.dir") + "/codesphere_workspace";

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;


    public ProjectController(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createProject(
            @RequestBody ProjectRequest request) throws IOException {
    	
    	Authentication auth =
    	        SecurityContextHolder.getContext().getAuthentication();

    	String username = auth.getName();

    	UserEntity user = userRepository.findByUsername(username)
    	        .orElseThrow(() -> new RuntimeException("User not found"));

    	
//    	String username = SecurityContextHolder
//    	        .getContext()
//    	        .getAuthentication()
//    	        .getName();
//
//    	UserEntity user = userRepository.findByUsername(username)
//    	        .orElseThrow(() -> new RuntimeException("User not found"));


        String projectName = request.getName().trim();

        // 1 Check DB
        if (projectRepository.existsByNameAndUser(projectName, user)) {

            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Project already exists", null));
        }

        // 2 Save in DB
        ProjectEntity project = new ProjectEntity();
        project.setName(request.getName());
        project.setUser(user);  
        projectRepository.save(project);


        // 3 Create folder
        Path basePath = Path.of(BASE_DIR);
        Files.createDirectories(basePath);

        Path projectPath = basePath.resolve(projectName);
        Files.createDirectories(projectPath);

        
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Project created successfully", projectName)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> listProjects() {

        // 1️⃣ Get logged-in user
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2️⃣ Fetch ONLY this user's projects
        List<String> projects = projectRepository.findByUser(user)
                .stream()
                .map(ProjectEntity::getName)
                .toList();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Projects fetched", projects)
        );
    }

}
