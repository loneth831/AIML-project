package com.cv.aiml_project.service;

import com.cv.aiml_project.entity.JobApplication;
import com.cv.aiml_project.entity.Resume;
import com.cv.aiml_project.entity.Role;
import com.cv.aiml_project.entity.User;
import com.cv.aiml_project.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ResumeService resumeService;  // Delegate resume operations

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private CandidateRankingRepository candidateRankingRepository;

    @Autowired
    private SkillMatchResultRepository skillMatchResultRepository;

    // ==================== USER CRUD OPERATIONS ====================

    public User registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User createUser(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> getUsersByRole(Role role) {
        return userRepository.findByRole(role);
    }

    public List<User> getActiveUsers() {
        return userRepository.findByIsActive(true);
    }

    public List<User> getCandidates() {
        return userRepository.findByRole(Role.CANDIDATE);
    }

    public List<User> getHRUsers() {
        return userRepository.findByRole(Role.HR);
    }

    public List<User> searchUsers(String keyword) {
        return userRepository.searchUsers(keyword);
    }

    public User updateUser(Long id, User updatedUser) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        existingUser.setFirstName(updatedUser.getFirstName());
        existingUser.setLastName(updatedUser.getLastName());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setPhone(updatedUser.getPhone());

        if (updatedUser.getRole() != null) {
            existingUser.setRole(updatedUser.getRole());
        }

        if (updatedUser.getSkills() != null) {
            existingUser.setSkills(updatedUser.getSkills());
        }
        if (updatedUser.getExperienceYears() != null) {
            existingUser.setExperienceYears(updatedUser.getExperienceYears());
        }
        if (updatedUser.getEducation() != null) {
            existingUser.setEducation(updatedUser.getEducation());
        }

        return userRepository.save(existingUser);
    }

    public User updatePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    public User updateRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(newRole);
        return userRepository.save(user);
    }

    public User deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(false);
        return userRepository.save(user);
    }

    public User activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(true);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        try {
            // For CANDIDATE users, need to delete all related records in correct order
            if (user.isCandidate()) {
                // 1. Delete candidate rankings first
                candidateRankingRepository.deleteByCandidateId(userId);

                // 2. Delete skill match results
                skillMatchResultRepository.deleteByCandidateId(userId);

                // 3. Handle job applications
                List<JobApplication> applications = jobApplicationRepository.findByCandidate(user);
                for (JobApplication app : applications) {
                    // Delete resume file if exists
                    if (app.getResumePath() != null) {
                        try {
                            Files.deleteIfExists(Paths.get(app.getResumePath()));
                        } catch (IOException e) {
                            System.err.println("Could not delete application resume file: " + app.getResumePath());
                        }
                    }
                }
                jobApplicationRepository.deleteAll(applications);

                // 4. Handle resumes last (since skill_match_results references them)
                List<Resume> resumes = resumeRepository.findByUser(user);
                for (Resume resume : resumes) {
                    // Delete the physical file
                    try {
                        Files.deleteIfExists(Paths.get(resume.getFilePath()));
                    } catch (IOException e) {
                        System.err.println("Could not delete resume file: " + resume.getFilePath());
                    }
                }
                resumeRepository.deleteAll(resumes);
            }

            // Finally delete the user
            userRepository.delete(user);

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    // ==================== RESUME OPERATIONS (delegated) ====================

    public void uploadResume(Long userId, MultipartFile file) throws IOException {
        resumeService.uploadResume(userId, file);
    }

    public Resource getResumeFile(Long userId) throws IOException {
        return resumeService.getCurrentResume(userId)
                .map(resume -> {
                    try {
                        return resumeService.getResumeFile(resume.getId());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to get resume file", e);
                    }
                })
                .orElseThrow(() -> new RuntimeException("No resume found for user"));
    }

    public void deleteResume(Long userId) throws IOException {
        resumeService.getCurrentResume(userId)
                .ifPresent(resume -> {
                    try {
                        resumeService.deleteResume(resume.getId());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to delete resume", e);
                    }
                });
    }

    public boolean hasResume(Long userId) {
        return resumeService.hasResume(userId);
    }

    // ==================== STATISTICS ====================

    public long getTotalUsers() {
        return userRepository.count();
    }

    public long getTotalCandidates() {
        return userRepository.countByRole(Role.CANDIDATE);
    }

    public long getTotalHR() {
        return userRepository.countByRole(Role.HR);
    }

    public long getActiveCandidates() {
        return userRepository.countByRoleAndIsActive(Role.CANDIDATE, true);
    }

    public long getMLProcessedUsers() {
        return resumeService.getProcessedResumeCount();
    }

    // ==================== VALIDATION ====================

    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public void updateMLData(Long userId, Double mlScore, Double mlConfidence, String extractedText) {
    }

    // Add this method to UserService.java to help load resumes
    public List<User> loadResumesForUsers(List<User> users) {
        users.forEach(user -> {
            if (user.getResumes() != null) {
                user.getResumes().size(); // Force initialization of resumes
            }
        });
        return users;
    }
}