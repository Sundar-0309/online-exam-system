package com.example.exam.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_results")
public class ExamResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    private int scoreAchieved;
    private int totalMarks;
    private LocalDateTime submissionTime;
    private LocalDateTime startedAt;
    private LocalDateTime terminatedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean webcamMonitoringEnabled;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int lookingAwayViolationCount;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int prohibitedDeviceViolationCount;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int mobilePhoneDetectionCount;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int earphoneDetectionCount;

    @Column(length = 255)
    private String detectedObjectTypes;

    @Column(length = 500)
    private String terminationReason;

    @Column(length = 20, nullable = false, columnDefinition = "varchar(20) default 'COMPLETED'")
    private String completionStatus = "IN_PROGRESS";

    private LocalDateTime lastLookingAwayViolationAt;
    private LocalDateTime lastDeviceViolationAt;

    // --- NEW ---
    // This links the result to the specific answers given
    @OneToMany(mappedBy = "examResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ExamAnswer> answers = new ArrayList<>();

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public Exam getExam() { return exam; }
    public void setExam(Exam exam) { this.exam = exam; }
    public int getScoreAchieved() { return scoreAchieved; }
    public void setScoreAchieved(int scoreAchieved) { this.scoreAchieved = scoreAchieved; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public LocalDateTime getSubmissionTime() { return submissionTime; }
    public void setSubmissionTime(LocalDateTime submissionTime) { this.submissionTime = submissionTime; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getTerminatedAt() { return terminatedAt; }
    public void setTerminatedAt(LocalDateTime terminatedAt) { this.terminatedAt = terminatedAt; }
    public boolean isWebcamMonitoringEnabled() { return webcamMonitoringEnabled; }
    public void setWebcamMonitoringEnabled(boolean webcamMonitoringEnabled) { this.webcamMonitoringEnabled = webcamMonitoringEnabled; }
    public int getLookingAwayViolationCount() { return lookingAwayViolationCount; }
    public void setLookingAwayViolationCount(int lookingAwayViolationCount) { this.lookingAwayViolationCount = lookingAwayViolationCount; }
    public int getProhibitedDeviceViolationCount() { return prohibitedDeviceViolationCount; }
    public void setProhibitedDeviceViolationCount(int prohibitedDeviceViolationCount) { this.prohibitedDeviceViolationCount = prohibitedDeviceViolationCount; }
    public int getMobilePhoneDetectionCount() { return mobilePhoneDetectionCount; }
    public void setMobilePhoneDetectionCount(int mobilePhoneDetectionCount) { this.mobilePhoneDetectionCount = mobilePhoneDetectionCount; }
    public int getEarphoneDetectionCount() { return earphoneDetectionCount; }
    public void setEarphoneDetectionCount(int earphoneDetectionCount) { this.earphoneDetectionCount = earphoneDetectionCount; }
    public String getDetectedObjectTypes() { return detectedObjectTypes; }
    public void setDetectedObjectTypes(String detectedObjectTypes) { this.detectedObjectTypes = detectedObjectTypes; }
    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }
    public String getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(String completionStatus) { this.completionStatus = completionStatus; }
    public LocalDateTime getLastLookingAwayViolationAt() { return lastLookingAwayViolationAt; }
    public void setLastLookingAwayViolationAt(LocalDateTime lastLookingAwayViolationAt) { this.lastLookingAwayViolationAt = lastLookingAwayViolationAt; }
    public LocalDateTime getLastDeviceViolationAt() { return lastDeviceViolationAt; }
    public void setLastDeviceViolationAt(LocalDateTime lastDeviceViolationAt) { this.lastDeviceViolationAt = lastDeviceViolationAt; }

    // --- NEW GETTER/SETTER ---
    public List<ExamAnswer> getAnswers() { return answers; }
    public void setAnswers(List<ExamAnswer> answers) { this.answers = answers; }

    // Helper method
    public void addAnswer(ExamAnswer answer) {
        answers.add(answer);
        answer.setExamResult(this);
    }
}

