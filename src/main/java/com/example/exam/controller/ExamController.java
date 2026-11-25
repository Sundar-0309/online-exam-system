package com.example.exam.controller;

import com.example.exam.model.*;
import com.example.exam.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/exam")
public class ExamController {

    @Value("${proctoring.face.missing-ms:3000}")
    private long faceMissingMs;

    @Value("${proctoring.face.turned-ms:2500}")
    private long faceTurnedMs;

    @Value("${proctoring.face.yaw-threshold:0.24}")
    private double faceYawThreshold;

    @Value("${proctoring.face.confidence:0.6}")
    private double faceConfidence;

    @Value("${proctoring.object.confidence:0.35}")
    private double objectConfidence;

    @Value("${proctoring.incident-cooldown-ms:12000}")
    private long incidentCooldownMs;

    @Value("${proctoring.object.scan-interval-ms:2500}")
    private long objectScanIntervalMs;

    @Value("${proctoring.object.confirmation-scans:2}")
    private int objectConfirmationScans;

    @Value("${proctoring.object.absence-scans:3}")
    private int objectAbsenceScans;

    @Value("${proctoring.normal-rearm-ms:2000}")
    private long normalRearmMs;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamResultRepository examResultRepository;

    @Autowired
    private UserRepository userRepository;


    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ExamAnswerRepository examAnswerRepository;

    @GetMapping("/{examId}")
    public String getExamPage(@PathVariable Long examId, Model model,
                              RedirectAttributes redirectAttributes) {

        // 1. Get the currently logged-in student
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName(); // This is the email
        User student = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        model.addAttribute("student", student);

        // 2. Get the exam
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        if (!isExamAssignedToStudent(student, exam)) {
            redirectAttributes.addFlashAttribute("errorMessage", "This exam is not assigned to you.");
            return "redirect:/student/dashboard";
        }
        if (examResultRepository.existsByExamAndStudent(exam, student)) {
            redirectAttributes.addFlashAttribute("errorMessage", "This exam attempt has already been started or submitted.");
            return "redirect:/student/dashboard";
        }
        model.addAttribute("exam", exam);

        // 3. Get the questions (force-load them)
        List<Question> questions = exam.getQuestions();
        questions.size(); // This forces the collection to load
        model.addAttribute("questions", questions);
        model.addAttribute("proctoringFaceMissingMs", faceMissingMs);
        model.addAttribute("proctoringFaceTurnedMs", faceTurnedMs);
        model.addAttribute("proctoringFaceYawThreshold", faceYawThreshold);
        model.addAttribute("proctoringFaceConfidence", faceConfidence);
        model.addAttribute("proctoringObjectConfidence", objectConfidence);
        model.addAttribute("proctoringIncidentCooldownMs", incidentCooldownMs);
        model.addAttribute("proctoringObjectScanIntervalMs", objectScanIntervalMs);
        model.addAttribute("proctoringObjectConfirmationScans", objectConfirmationScans);
        model.addAttribute("proctoringObjectAbsenceScans", objectAbsenceScans);
        model.addAttribute("proctoringNormalRearmMs", normalRearmMs);

        return "student/exam_page";
    }

    @PostMapping("/{examId}/start")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> startExam(@PathVariable Long examId) {
        User student = getAuthenticatedStudent();
        Exam exam = examRepository.findByIdForUpdate(examId).orElse(null);
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isExamAssignedToStudent(student, exam)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This exam is not assigned to you."));
        }
        ExamResult activeAttempt = examResultRepository
                .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(exam, student, "IN_PROGRESS")
                .orElse(null);
        if (activeAttempt != null) {
            return ResponseEntity.ok(Map.of("attemptId", activeAttempt.getId()));
        }
        if (examResultRepository.existsByExamAndStudent(exam, student)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An attempt for this exam already exists."));
        }

        ExamResult attempt = new ExamResult();
        attempt.setStudent(student);
        attempt.setExam(exam);
        attempt.setWebcamMonitoringEnabled(exam.isWebcamMonitoringEnabled());
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setTotalMarks(exam.getQuestions().stream().mapToInt(Question::getMarks).sum());
        attempt.setCompletionStatus("IN_PROGRESS");
        examResultRepository.save(attempt);

        return ResponseEntity.ok(Map.of("attemptId", attempt.getId()));
    }

    @PostMapping("/proctoring/violation")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> reportViolation(
            @RequestBody Map<String, String> violation) {
        User student = getAuthenticatedStudent();
        Long examId;
        try {
            examId = Long.valueOf(violation.getOrDefault("examId", ""));
        } catch (NumberFormatException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid exam id."));
        }

        Exam exam = examRepository.findById(examId).orElse(null);
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isExamAssignedToStudent(student, exam)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This exam is not assigned to you."));
        }
        ExamResult attempt = examResultRepository
                .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(exam, student, "IN_PROGRESS")
                .orElse(null);
        if (attempt == null || !attempt.isWebcamMonitoringEnabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No active webcam-monitored attempt exists."));
        }

        String category = violation.getOrDefault("category", "");
        LocalDateTime now = LocalDateTime.now();
        boolean counted;
        int warningCount;
        int maximumWarnings;

        if ("looking_away".equals(category)) {
            maximumWarnings = 8;
            LocalDateTime lastAt = attempt.getLastLookingAwayViolationAt();
            counted = lastAt == null || !now.isBefore(lastAt.plusSeconds(5));
            if (counted) {
                warningCount = Math.min(maximumWarnings, attempt.getLookingAwayViolationCount() + 1);
                attempt.setLookingAwayViolationCount(warningCount);
                attempt.setLastLookingAwayViolationAt(now);
            } else {
                warningCount = attempt.getLookingAwayViolationCount();
            }
            if (counted && warningCount >= maximumWarnings) {
                terminate(attempt, "Maximum looking-away violations reached.");
            }
        } else if ("prohibited_device".equals(category)) {
            String objectType = violation.getOrDefault("objectType", "");
            if (!"mobile_phone".equals(objectType) && !"earphones".equals(objectType)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported prohibited object type."));
            }
            maximumWarnings = 2;
            LocalDateTime lastAt = attempt.getLastDeviceViolationAt();
            counted = lastAt == null || !now.isBefore(lastAt.plusSeconds(5));
            if (counted) {
                warningCount = Math.min(maximumWarnings, attempt.getProhibitedDeviceViolationCount() + 1);
                attempt.setProhibitedDeviceViolationCount(warningCount);
                attempt.setLastDeviceViolationAt(now);
                if ("mobile_phone".equals(objectType)) {
                    attempt.setMobilePhoneDetectionCount(attempt.getMobilePhoneDetectionCount() + 1);
                } else {
                    attempt.setEarphoneDetectionCount(attempt.getEarphoneDetectionCount() + 1);
                }
                String objectLabel = "mobile_phone".equals(objectType) ? "Mobile phone" : "Earphones/earbuds";
                String existingTypes = attempt.getDetectedObjectTypes();
                if (existingTypes == null || !existingTypes.contains(objectLabel)) {
                    attempt.setDetectedObjectTypes(existingTypes == null
                            ? objectLabel : existingTypes + ", " + objectLabel);
                }
            } else {
                warningCount = attempt.getProhibitedDeviceViolationCount();
            }
            if (counted && warningCount >= maximumWarnings) {
                terminate(attempt, "Second prohibited-device violation detected (" + objectType + ").");
            }
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported violation category."));
        }

        examResultRepository.save(attempt);
        return ResponseEntity.ok(Map.of(
                "counted", counted,
                "warningCount", warningCount,
                "maximumWarnings", maximumWarnings,
                "terminated", "TERMINATED".equals(attempt.getCompletionStatus()),
                "terminationReason", attempt.getTerminationReason() == null ? "" : attempt.getTerminationReason()));
    }

    @PostMapping("/answer")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, String>> saveAnswer(@RequestBody Map<String, String> submittedAnswer) {
        User student = getAuthenticatedStudent();
        Long examId;
        Long questionId;
        int selectedOption;
        try {
            examId = Long.valueOf(submittedAnswer.getOrDefault("examId", ""));
            questionId = Long.valueOf(submittedAnswer.getOrDefault("questionId", ""));
            selectedOption = Integer.parseInt(submittedAnswer.getOrDefault("selectedOption", ""));
        } catch (NumberFormatException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid answer details."));
        }
        if (selectedOption < 1 || selectedOption > 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid selected option."));
        }

        Exam exam = examRepository.findById(examId).orElse(null);
        Question question = questionRepository.findById(questionId).orElse(null);
        if (exam == null || question == null || question.getExam() == null
                || !exam.getId().equals(question.getExam().getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question does not belong to this exam."));
        }
        if (!isExamAssignedToStudent(student, exam)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This exam is not assigned to you."));
        }
        ExamResult attempt = examResultRepository
                .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(exam, student, "IN_PROGRESS")
                .orElse(null);
        if (attempt == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No active attempt exists."));
        }

        ExamAnswer answer = examAnswerRepository.findByExamResultAndQuestion(attempt, question)
                .orElseGet(() -> new ExamAnswer(attempt, question, selectedOption));
        answer.setSelectedOption(selectedOption);
        answer.setExamResult(attempt);
        answer.setQuestion(question);
        examAnswerRepository.save(answer);
        return ResponseEntity.ok(Map.of("saved", "true"));
    }

    @PostMapping("/proctoring/failure")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> reportProctoringFailure(
            @RequestBody Map<String, String> failure) {
        User student = getAuthenticatedStudent();
        Long examId;
        try {
            examId = Long.valueOf(failure.getOrDefault("examId", ""));
        } catch (NumberFormatException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid exam id."));
        }
        Exam exam = examRepository.findById(examId).orElse(null);
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isExamAssignedToStudent(student, exam)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "This exam is not assigned to you."));
        }
        ExamResult attempt = examResultRepository
                .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(exam, student, "IN_PROGRESS")
                .orElse(null);
        if (attempt == null || !attempt.isWebcamMonitoringEnabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No active webcam-monitored attempt exists."));
        }
        String detail = failure.get("reason");
        if (detail == null || detail.isBlank()) {
            detail = "Camera monitoring stopped unexpectedly.";
        }
        if (detail.length() > 250) {
            detail = detail.substring(0, 250);
        }
        terminate(attempt, "Proctoring could not continue: " + detail);
        examResultRepository.save(attempt);
        return ResponseEntity.ok(Map.of("terminated", true));
    }

    @PostMapping("/submit")
    @Transactional
    public String submitExam(@RequestParam Long examId,
                             @RequestParam Map<String, String> submittedAnswers,
                             @RequestParam(name = "terminationReason", defaultValue = "") String terminationReason,
                             RedirectAttributes redirectAttributes) {

        User student = getAuthenticatedStudent();

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        if (!isExamAssignedToStudent(student, exam)) {
            redirectAttributes.addFlashAttribute("errorMessage", "This exam is not assigned to you.");
            return "redirect:/student/dashboard";
        }

        ExamResult result = examResultRepository
                .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(exam, student, "IN_PROGRESS")
                .orElseGet(() -> examResultRepository
                        .findFirstByExamAndStudentAndCompletionStatusOrderByStartedAtDesc(exam, student, "TERMINATED")
                        .orElse(null));
        if (result == null || result.getSubmissionTime() != null) {
            redirectAttributes.addFlashAttribute("errorMessage", "No active attempt is available to submit.");
            return "redirect:/student/dashboard";
        }

        int totalMarks = 0;
        int scoreAchieved = 0;

        Map<Long, ExamAnswer> savedAnswers = new HashMap<>();
        for (ExamAnswer savedAnswer : examAnswerRepository.findByExamResult(result)) {
            savedAnswers.put(savedAnswer.getQuestion().getId(), savedAnswer);
        }
        boolean useSavedAnswers = result.isWebcamMonitoringEnabled()
                && "TERMINATED".equals(result.getCompletionStatus());

        // Loop through all questions in the exam
        for (Question question : exam.getQuestions()) {
            totalMarks += question.getMarks();
            String answerKey = "q_" + question.getId();

            int selectedOption = 0; // Default to 0 (unanswered)

            if (useSavedAnswers) {
                ExamAnswer savedAnswer = savedAnswers.get(question.getId());
                if (savedAnswer != null) {
                    selectedOption = savedAnswer.getSelectedOption();
                }
            } else if (submittedAnswers.containsKey(answerKey)) {
                try {
                    selectedOption = Integer.parseInt(submittedAnswers.get(answerKey));
                } catch (NumberFormatException e) {
                    selectedOption = 0; // Handle invalid data just in case
                }

                if (selectedOption < 1 || selectedOption > 4) {
                    selectedOption = 0;
                }
            }

            if (selectedOption >= 1 && selectedOption <= 4
                    && selectedOption == question.getCorrectAnswer()) {
                scoreAchieved += question.getMarks();
            }

            ExamAnswer examAnswer = savedAnswers.get(question.getId());
            if (examAnswer == null) {
                examAnswer = new ExamAnswer(result, question, selectedOption);
                result.addAnswer(examAnswer);
            } else {
                examAnswer.setSelectedOption(selectedOption);
            }
            examAnswerRepository.save(examAnswer);
        }

        result.setScoreAchieved(scoreAchieved);
        result.setTotalMarks(totalMarks);
        result.setSubmissionTime(LocalDateTime.now());
        if (result.getTerminationReason() == null && !terminationReason.isBlank()) {
            result.setTerminationReason(terminationReason.substring(0, Math.min(terminationReason.length(), 500)));
            result.setCompletionStatus("TERMINATED");
            result.setTerminatedAt(LocalDateTime.now());
        } else if (!"TERMINATED".equals(result.getCompletionStatus())) {
            result.setCompletionStatus("COMPLETED");
        }
        examResultRepository.save(result);

        // Pass data to the result page
        redirectAttributes.addFlashAttribute("score", scoreAchieved);
        redirectAttributes.addFlashAttribute("total", totalMarks);

        // Pass the result ID for the review button
        redirectAttributes.addFlashAttribute("resultId", result.getId());
        if ("TERMINATED".equals(result.getCompletionStatus())) {
            redirectAttributes.addFlashAttribute("examTerminated", true);
            redirectAttributes.addFlashAttribute("terminationReason", result.getTerminationReason());
        }

        return "redirect:/student/result";
    }

    private User getAuthenticatedStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Student not found"));
    }

    private boolean isExamAssignedToStudent(User student, Exam exam) {
        return student.getAssignedExams().stream()
                .anyMatch(assignedExam -> exam.getId().equals(assignedExam.getId()));
    }

    private void terminate(ExamResult attempt, String reason) {
        attempt.setCompletionStatus("TERMINATED");
        attempt.setTerminationReason(reason);
        attempt.setTerminatedAt(LocalDateTime.now());
    }
}

