package com.cv.aiml_project.service;

import com.cv.aiml_project.entity.*;
import com.cv.aiml_project.repository.JobRepository;
import com.cv.aiml_project.repository.ResumeRepository;
import com.cv.aiml_project.repository.SkillMatchResultRepository;
import com.cv.aiml_project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SkillMatchResultService {

    @Autowired
    private SkillMatchResultRepository skillMatchResultRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private AIMatchingService aiMatchingService;

    // ==================== CREATE/UPDATE OPERATIONS ====================

    /**
     * Create or update skill match result for a job and candidate
     */
    @Transactional
    public SkillMatchResult createOrUpdateMatchResult(Long jobId, Long candidateId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found with id: " + candidateId));

        if (!candidate.isCandidate()) {
            throw new RuntimeException("User is not a candidate");
        }

        Resume currentResume = candidate.getCurrentResume();
        if (currentResume == null) {
            throw new RuntimeException("Candidate has no resume uploaded");
        }

        // Set previous results as not latest
        skillMatchResultRepository.setNotLatestForJobAndCandidate(jobId, candidateId);

        // Create new match result
        SkillMatchResult result = new SkillMatchResult(job, candidate);
        result.setResume(currentResume);
        result.setLatest(true);
        result.setRecalculationCount(1);
        result.setLastRecalculatedDate(LocalDateTime.now());

        // Calculate scores using AI matching service
        calculateMatchScores(result, job, candidate, currentResume);

        // Extract structured data from resume
        extractStructuredData(result, currentResume);

        // Determine matched/missing skills
        analyzeSkillMatch(result, job, candidate);

        // Set AI processing status
        result.setAiProcessed(true);
        result.setAiModelVersion("1.0.0");
        result.setAiConfidence(85.0 + (Math.random() * 10)); // Simulated confidence

        return skillMatchResultRepository.save(result);
    }

    /**
     * Calculate match scores for the result
     */
    private void calculateMatchScores(SkillMatchResult result, Job job, User candidate, Resume resume) {
        // Get overall score from AIMatchingService
        Double overallScore = aiMatchingService.calculateJobMatchScore(job, candidate);
        result.setOverallScore(overallScore);

        // Get component scores
        Map<String, Double> componentScores = aiMatchingService.calculateComponentScores(job, candidate);
        result.setSkillsScore(componentScores.get("skills"));
        result.setExperienceScore(componentScores.get("experience"));
        result.setEducationScore(componentScores.get("education"));

        // Simulate additional scores (personality, cultural fit)
        result.setPersonalityScore(70.0 + (Math.random() * 25));
        result.setCulturalFitScore(65.0 + (Math.random() * 30));
    }

    /**
     * Extract structured data from resume
     */
    private void extractStructuredData(SkillMatchResult result, Resume resume) {
        // In a real implementation, this would parse the extracted text
        // For now, use existing candidate profile data or simulated data
        User candidate = result.getCandidate();

        // Extract skills (from candidate profile)
        result.setExtractedSkills(candidate.getSkills());

        // Extract experience
        if (candidate.getExperienceYears() != null) {
            result.setExtractedExperience(candidate.getExperienceYears() + " years of experience");
        } else {
            result.setExtractedExperience("Experience not specified");
        }

        // Extract education
        result.setExtractedEducation(candidate.getEducation());

        // Extract certifications (simulated)
        result.setExtractedCertifications("AWS Certified Developer, Scrum Master");

        // Extract languages (simulated)
        result.setExtractedLanguages("English (Fluent), Spanish (Conversational)");

        // Extract projects (simulated)
        result.setExtractedProjects("E-commerce Platform, Mobile App Development, Data Analytics Dashboard");

        // Set raw extracted data
        if (resume.getExtractedText() != null) {
            result.setRawExtractedData(resume.getExtractedText().substring(0, Math.min(500, resume.getExtractedText().length())));
        }
    }

    /**
     * Analyze skill match between job and candidate
     */
    private void analyzeSkillMatch(SkillMatchResult result, Job job, User candidate) {
        if (job.getRequiredSkills() == null || candidate.getSkills() == null) {
            result.setMatchedSkills("");
            result.setMissingSkills("");
            result.setPartialSkills("");
            return;
        }

        Set<String> requiredSkills = Arrays.stream(job.getRequiredSkills().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> candidateSkills = Arrays.stream(candidate.getSkills().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> matched = new HashSet<>();
        Set<String> missing = new HashSet<>();
        Set<String> partial = new HashSet<>();

        for (String required : requiredSkills) {
            boolean found = false;
            boolean partialFound = false;

            for (String candidateSkill : candidateSkills) {
                if (candidateSkill.equals(required)) {
                    found = true;
                    break;
                } else if (candidateSkill.contains(required) || required.contains(candidateSkill)) {
                    partialFound = true;
                }
            }

            if (found) {
                matched.add(required);
            } else if (partialFound) {
                partial.add(required);
            } else {
                missing.add(required);
            }
        }

        result.setMatchedSkills(String.join(", ", matched));
        result.setMissingSkills(String.join(", ", missing));
        result.setPartialSkills(String.join(", ", partial));
    }

    /**
     * Recalculate score for an existing match result
     */
    @Transactional
    public SkillMatchResult recalculateScore(Long resultId) {
        SkillMatchResult result = skillMatchResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("Match result not found with id: " + resultId));

        Job job = result.getJob();
        User candidate = result.getCandidate();

        // Increment recalculation count
        result.setRecalculationCount(result.getRecalculationCount() + 1);
        result.setLastRecalculatedDate(LocalDateTime.now());

        // Recalculate scores
        calculateMatchScores(result, job, candidate, result.getResume());

        // Re-analyze skill match
        analyzeSkillMatch(result, job, candidate);

        // Update AI processing info
        result.setAiProcessed(true);
        result.setAiConfidence(85.0 + (Math.random() * 10));

        return skillMatchResultRepository.save(result);
    }

    // ==================== READ OPERATIONS ====================

    /**
     * Get match result by ID
     */
    public Optional<SkillMatchResult> getMatchResultById(Long id) {
        return skillMatchResultRepository.findById(id);
    }

    /**
     * Get all match results for a job
     */
    public List<SkillMatchResult> getMatchResultsForJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));
        return skillMatchResultRepository.findByJobOrderByOverallScoreDesc(job);
    }

    /**
     * Get latest match results for a job (ranked)
     */
    @Transactional(readOnly = true)
    public List<SkillMatchResult> getLatestMatchResultsForJob(Long jobId) {
        return skillMatchResultRepository.findLatestByJobOrderByScoreDesc(jobId);
    }

    /**
     * Get match results for a candidate
     */
    public List<SkillMatchResult> getMatchResultsForCandidate(Long candidateId) {
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        return skillMatchResultRepository.findByCandidateOrderByMatchDateDesc(candidate);
    }

    /**
     * Get latest match result for a specific job and candidate
     */
    public Optional<SkillMatchResult> getLatestMatchForJobAndCandidate(Long jobId, Long candidateId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        return skillMatchResultRepository.findByJobAndCandidateAndIsLatestTrue(job, candidate);
    }

    /**
     * Get top N matches for a job
     */
    public List<SkillMatchResult> getTopMatchesForJob(Long jobId, int limit, Double minScore) {
        List<SkillMatchResult> allMatches = skillMatchResultRepository.findTopMatchesForJob(jobId, minScore != null ? minScore : 0.0);
        return allMatches.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Get match statistics for a job
     */
    public Map<String, Object> getMatchStatisticsForJob(Long jobId) {
        Map<String, Object> stats = new HashMap<>();

        long totalMatches = skillMatchResultRepository.countByJobId(jobId);
        Double avgScore = skillMatchResultRepository.getAverageScoreForJob(jobId);
        Double maxScore = skillMatchResultRepository.getMaxScoreForJob(jobId);
        Double minScore = skillMatchResultRepository.getMinScoreForJob(jobId);

        stats.put("totalMatches", totalMatches);
        stats.put("averageScore", avgScore != null ? avgScore : 0.0);
        stats.put("maxScore", maxScore != null ? maxScore : 0.0);
        stats.put("minScore", minScore != null ? minScore : 0.0);

        // Score distribution
        long excellentMatches = skillMatchResultRepository.findByScoreRange(jobId, 80.0, 100.0).size();
        long goodMatches = skillMatchResultRepository.findByScoreRange(jobId, 60.0, 79.9).size();
        long averageMatches = skillMatchResultRepository.findByScoreRange(jobId, 40.0, 59.9).size();
        long poorMatches = skillMatchResultRepository.findByScoreRange(jobId, 0.0, 39.9).size();

        stats.put("excellentMatches", excellentMatches);
        stats.put("goodMatches", goodMatches);
        stats.put("averageMatches", averageMatches);
        stats.put("poorMatches", poorMatches);

        return stats;
    }

    /**
     * Get all processed/unprocessed matches
     */
    public List<SkillMatchResult> getProcessedMatches(boolean processed) {
        return skillMatchResultRepository.findByAiProcessed(processed);
    }

    // ==================== DELETE OPERATIONS ====================

    /**
     * Delete a specific match result
     */
    @Transactional
    public void deleteMatchResult(Long resultId) {
        SkillMatchResult result = skillMatchResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("Match result not found"));
        skillMatchResultRepository.delete(result);
    }

    /**
     * Remove all match results for a job (soft delete)
     */
    @Transactional
    public void deactivateAllForJob(Long jobId) {
        skillMatchResultRepository.deactivateAllForJob(jobId);
    }

    /**
     * Permanently delete all match results for a job
     */
    @Transactional
    public void deleteAllForJob(Long jobId) {
        skillMatchResultRepository.deleteByJobId(jobId);
    }

    /**
     * Remove match records (soft delete)
     */
    @Transactional
    public void removeMatchRecord(Long resultId) {
        SkillMatchResult result = skillMatchResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("Match result not found"));
        result.setActive(false);
        skillMatchResultRepository.save(result);
    }

    // ==================== BATCH OPERATIONS ====================

    /**
     * Process all candidates for a job
     */
    @Transactional
    public int processAllCandidatesForJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        List<User> candidates = userRepository.findByRole(Role.CANDIDATE);
        int processed = 0;

        for (User candidate : candidates) {
            if (candidate.hasResume()) {
                try {
                    createOrUpdateMatchResult(jobId, candidate.getId());
                    processed++;
                } catch (Exception e) {
                    // Log error but continue processing others
                    System.err.println("Failed to process candidate " + candidate.getId() + ": " + e.getMessage());
                }
            }
        }

        return processed;
    }

    /**
     * Batch recalculate scores for a job
     */
    @Transactional
    public int batchRecalculateForJob(Long jobId) {
        List<SkillMatchResult> results = skillMatchResultRepository.findLatestByJobOrderByScoreDesc(jobId);
        int recalculated = 0;

        for (SkillMatchResult result : results) {
            try {
                recalculateScore(result.getId());
                recalculated++;
            } catch (Exception e) {
                System.err.println("Failed to recalculate result " + result.getId() + ": " + e.getMessage());
            }
        }

        return recalculated;
    }

    // ==================== RANKING OPERATIONS ====================

    /**
     * Update ranking positions for a job
     */
    @Transactional
    public void updateRankingForJob(Long jobId) {
        List<SkillMatchResult> results = skillMatchResultRepository.findLatestByJobOrderByScoreDesc(jobId);
        int total = results.size();

        for (int i = 0; i < results.size(); i++) {
            SkillMatchResult result = results.get(i);
            result.setRankPosition(i + 1);
            result.setTotalCandidatesRanked(total);

            if (total > 0) {
                double percentile = ((total - i - 1) * 100.0) / total;
                result.setPercentile(percentile);
            }
        }

        skillMatchResultRepository.saveAll(results);
    }


    /**
     * Get ranked candidates for a job
     */
    public List<Map<String, Object>> getRankedCandidatesForJob(Long jobId) {
        List<SkillMatchResult> results = skillMatchResultRepository.findLatestByJobOrderByScoreDesc(jobId);
        List<Map<String, Object>> rankedList = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            SkillMatchResult result = results.get(i);
            User candidate = result.getCandidate();

            Map<String, Object> rankedCandidate = new HashMap<>();
            rankedCandidate.put("rank", i + 1);
            rankedCandidate.put("candidateId", candidate.getId());
            rankedCandidate.put("candidateName", candidate.getFullName());
            rankedCandidate.put("candidateEmail", candidate.getEmail());
            rankedCandidate.put("overallScore", result.getOverallScore());
            rankedCandidate.put("skillsScore", result.getSkillsScore());
            rankedCandidate.put("experienceScore", result.getExperienceScore());
            rankedCandidate.put("educationScore", result.getEducationScore());
            rankedCandidate.put("matchedSkills", result.getMatchedSkills());
            rankedCandidate.put("missingSkills", result.getMissingSkills());
            rankedCandidate.put("matchLevel", result.getMatchLevel());
            rankedCandidate.put("percentile", result.getPercentile());

            rankedList.add(rankedCandidate);
        }

        return rankedList;
    }
}
