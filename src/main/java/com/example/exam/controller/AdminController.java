package com.example.exam.controller;

import com.example.exam.model.Exam;
import com.example.exam.model.ExamResult;
import com.example.exam.model.Question;
import com.example.exam.model.User;
import com.example.exam.repository.ExamRepository;
import com.example.exam.repository.ExamResultRepository;
import com.example.exam.repository.QuestionRepository;
import com.example.exam.repository.UserRepository;
import com.example.exam.service.StudentExcelImportService;
import com.example.exam.service.StudentImportRecord;
import com.example.exam.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuestionRepository questionRepository;


    @Autowired
    private ExamResultRepository examResultRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private StudentExcelImportService studentExcelImportService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {

        // 1. Get KPI Card Stats
        long totalStudents = userRepository.countByRole("ROLE_STUDENT");
        long totalExams = examRepository.count();
        long totalQuestions = questionRepository.count();
        long totalSubmissions = examResultRepository.countByCompletionStatusNot("IN_PROGRESS");

        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("totalExams", totalExams);
        model.addAttribute("totalQuestions", totalQuestions);
        model.addAttribute("totalSubmissions", totalSubmissions);

        // 2. Get Data for Submissions Chart
        List<Exam> allExams = examRepository.findAll();
        List<String> chartLabels = allExams.stream()
                .map(Exam::getTitle)
                .collect(Collectors.toList());
        List<Integer> chartData = allExams.stream()
                .map(exam -> (int) exam.getResults().stream()
                        .filter(result -> !"IN_PROGRESS".equals(result.getCompletionStatus()))
                        .count())
                .collect(Collectors.toList());

        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);


        List<ExamResult> recentResults =
                examResultRepository.findTop5ByCompletionStatusNotOrderBySubmissionTimeDesc("IN_PROGRESS");
        model.addAttribute("recentResults", recentResults);

        return "admin/dashboard";
    }


    @GetMapping("/manage-exams")
    public String getManageExamsPage(Model model) {
        model.addAttribute("exams", examRepository.findAll());
        return "admin/manage_exams";
    }

    @GetMapping("/exam/add")
    public String showAddExamForm(Model model) {
        model.addAttribute("exam", new Exam());
        return "admin/add_exam";
    }

    @PostMapping("/exam/add")
    public String addExam(@ModelAttribute Exam exam) {
        examRepository.save(exam);
        return "redirect:/admin/manage-exams";
    }

    @GetMapping("/exam/{examId}/question/add")
    public String showAddQuestionForm(@PathVariable Long examId, Model model) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        Question question = new Question();
        question.setExam(exam);
        model.addAttribute("question", question);
        model.addAttribute("examId", examId);
        return "admin/add_question";
    }


    @PostMapping("/exam/question/add")
    public String addQuestion(@ModelAttribute Question question,
                              @RequestParam Long examId,
                              RedirectAttributes redirectAttributes) {

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));

        question.setExam(exam);
        questionRepository.save(question);

        redirectAttributes.addFlashAttribute("successMessage", "Question added successfully!");

        return "redirect:/admin/exam/" + examId + "/question/add";
    }

    @GetMapping("/results/{examId}")
    public String viewResults(@PathVariable Long examId, Model model) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));

        List<ExamResult> results = examResultRepository.findByExam(exam);

        model.addAttribute("exam", exam);
        model.addAttribute("results", results);

        return "admin/view_results";
    }

    @GetMapping("/results")
    public String viewAllResults(@RequestParam(name = "studentId", required = false) Long studentId,
                                 @RequestParam(name = "examId", required = false) Long examId,
                                 Model model) {
        model.addAttribute("students", userRepository.findByRole("ROLE_STUDENT"));
        model.addAttribute("exams", examRepository.findAll());
        model.addAttribute("results", examResultRepository.findAllFiltered(studentId, examId));
        model.addAttribute("selectedStudentId", studentId);
        model.addAttribute("selectedExamId", examId);
        return "admin/all_results";
    }

    @GetMapping("/exam/delete/{examId}")
    public String deleteExam(@PathVariable Long examId) {
        // CascadeType.ALL, deleting the exam will also delete
        // all associated questions and results.
        examRepository.deleteById(examId);
        return "redirect:/admin/manage-exams";
    }

    @GetMapping("/exam/edit/{examId}")
    public String showEditExamForm(@PathVariable Long examId, Model model) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        model.addAttribute("exam", exam);
        return "admin/edit_exam";
    }
    /***
     Subscribe Lazycoder - https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1
     whatsapp - https://wa.me/919572181024
     email - wapka1503@gmail.com
     ***/
    @PostMapping("/exam/update/{examId}")
    public String updateExam(@PathVariable Long examId, @ModelAttribute Exam updatedExam) {
        Exam existingExam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));

        existingExam.setTitle(updatedExam.getTitle());
        existingExam.setDurationInMinutes(updatedExam.getDurationInMinutes());
        existingExam.setWebcamMonitoringEnabled(updatedExam.isWebcamMonitoringEnabled());

        examRepository.save(existingExam);
        return "redirect:/admin/manage-exams";
    }

    @GetMapping("/exam/manage-questions/{examId}")
    public String showManageQuestionsPage(@PathVariable Long examId, Model model) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid exam Id:" + examId));
        model.addAttribute("exam", exam);
        model.addAttribute("questions", exam.getQuestions());
        return "admin/manage_questions";
    }

    @GetMapping("/question/edit/{questionId}")
    public String showEditQuestionForm(@PathVariable Long questionId, Model model) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid question Id:" + questionId));
        model.addAttribute("question", question);
        return "admin/edit_question.html";
    }

    @PostMapping("/question/update/{questionId}")
    public String updateQuestion(@PathVariable Long questionId, @ModelAttribute Question updatedQuestion) {
        Question existingQuestion = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid question Id:" + questionId));

        existingQuestion.setText(updatedQuestion.getText());
        existingQuestion.setOption1(updatedQuestion.getOption1());
        existingQuestion.setOption2(updatedQuestion.getOption2());
        existingQuestion.setOption3(updatedQuestion.getOption3());
        existingQuestion.setOption4(updatedQuestion.getOption4());
        existingQuestion.setCorrectAnswer(updatedQuestion.getCorrectAnswer());
        existingQuestion.setMarks(updatedQuestion.getMarks());

        questionRepository.save(existingQuestion);

        return "redirect:/admin/exam/manage-questions/" + existingQuestion.getExam().getId();
    }

    @GetMapping("/exam/{examId}/question/delete/{questionId}")
    public String deleteQuestion(@PathVariable Long examId,
                                 @PathVariable Long questionId,
                                 RedirectAttributes redirectAttributes) { // <-- Added RedirectAttributes
        try {
            questionRepository.deleteById(questionId);
            redirectAttributes.addFlashAttribute("successMessage", "Question deleted successfully.");

        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete this question. It has already been answered by one or more students and is part of their exam results.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while trying to delete the question.");
        }

        return "redirect:/admin/exam/manage-questions/" + examId;
    }


    @GetMapping("/students")
    public String getManageStudentsPage(Model model) {
        // Find all users who have the "STUDENT" role
        List<User> students = userRepository.findByRole("ROLE_STUDENT");
        Map<Long, Set<Long>> assignedExamIdsByStudent = students.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        student -> student.getAssignedExams().stream()
                                .map(Exam::getId)
                                .collect(Collectors.toSet())
                ));
        model.addAttribute("students", students);
        model.addAttribute("exams", examRepository.findAll());
        model.addAttribute("assignedExamIdsByStudent", assignedExamIdsByStudent);
        return "admin/manage_students";
    }

    @PostMapping("/students/{studentId}/exams")
    @Transactional
    public String assignExams(@PathVariable Long studentId,
                              @RequestParam(name = "examIds", required = false) List<Long> examIds,
                              RedirectAttributes redirectAttributes) {
        User student = userRepository.findById(studentId).orElse(null);
        if (student == null || !"ROLE_STUDENT".equals(student.getRole())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Student account was not found.");
            return "redirect:/admin/students";
        }

        if (examIds != null && examIds.stream().anyMatch(id -> id == null)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid exam selection.");
            return "redirect:/admin/students";
        }

        List<Exam> selectedExams = new ArrayList<>();
        if (examIds != null && !examIds.isEmpty()) {
            selectedExams.addAll(examRepository.findAllById(examIds));
        }
        int requestedCount = examIds == null ? 0 : (int) examIds.stream().distinct().count();
        if (selectedExams.size() != requestedCount) {
            redirectAttributes.addFlashAttribute("errorMessage", "One or more selected exams were not found.");
            return "redirect:/admin/students";
        }

        student.getAssignedExams().clear();
        student.getAssignedExams().addAll(selectedExams);
        userRepository.save(student);
        redirectAttributes.addFlashAttribute("successMessage",
                "Exam assignments updated for " + student.getUsername() + ".");
        return "redirect:/admin/students";
    }

    @PostMapping("/students/add")
    public String addStudent(@RequestParam(name = "fullName", required = false) String fullName,
                             @RequestParam(name = "username", required = false) String username,
                             @RequestParam(name = "password", required = false) String password,
                             RedirectAttributes redirectAttributes) {
        String normalizedFullName = fullName == null ? "" : fullName.trim();
        String normalizedUsername = username == null ? "" : username.trim();

        if (normalizedFullName.isBlank() || normalizedUsername.isBlank()
                || password == null || password.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Full name, username, and password are required.");
            return "redirect:/admin/students";
        }

        if (normalizedFullName.length() > 255 || normalizedUsername.length() > 255) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Full name and username must be 255 characters or fewer.");
            return "redirect:/admin/students";
        }

        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An account already exists with that username.");
            return "redirect:/admin/students";
        }

        User student = new User();
        student.setFullName(normalizedFullName);
        student.setUsername(normalizedUsername);
        student.setPassword(password);

        try {
            userService.saveStudent(student);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Student account created successfully.");
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An account already exists with that username.");
        }

        return "redirect:/admin/students";
    }

    @PostMapping("/students/upload")
    public String uploadStudents(@RequestParam(name = "file", required = false) MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        try {
            List<StudentImportRecord> records = studentExcelImportService.importStudents(file);
            long successfulCount = records.stream().filter(StudentImportRecord::isSuccess).count();
            long failedCount = records.size() - successfulCount;

            redirectAttributes.addFlashAttribute("importResults", records);
            redirectAttributes.addFlashAttribute("importSummary",
                    successfulCount + " student account(s) created; "
                            + failedCount + " record(s) failed.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "The uploaded Excel file could not be read. Please try again.");
        }

        return "redirect:/admin/students";
    }

    @GetMapping("/student/edit/{id}")
    public String showEditStudentForm(@PathVariable Long id, Model model) {
        User student = userRepository.findById(id)
                .filter(user -> "ROLE_STUDENT".equals(user.getRole()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid student Id:" + id));
        model.addAttribute("student", student);
        return "admin/edit_student";
    }

    @PostMapping("/student/update/{id}")
    public String updateStudent(@PathVariable Long id,
                                @RequestParam(name = "fullName", required = false) String fullName,
                                @RequestParam(name = "username", required = false) String username,
                                RedirectAttributes redirectAttributes) {
        User student = userRepository.findById(id)
                .filter(user -> "ROLE_STUDENT".equals(user.getRole()))
                .orElse(null);
        if (student == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Student account was not found.");
            return "redirect:/admin/students";
        }

        String normalizedFullName = fullName == null ? "" : fullName.trim();
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedFullName.isBlank() || normalizedUsername.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Student name and username are required.");
            return "redirect:/admin/student/edit/" + id;
        }
        if (normalizedFullName.length() > 255 || normalizedUsername.length() > 255) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Student name and username must be 255 characters or fewer.");
            return "redirect:/admin/student/edit/" + id;
        }

        User usernameOwner = userRepository.findByUsername(normalizedUsername).orElse(null);
        if (usernameOwner != null && !usernameOwner.getId().equals(student.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An account already exists with that username.");
            return "redirect:/admin/student/edit/" + id;
        }

        student.setFullName(normalizedFullName);
        student.setUsername(normalizedUsername);
        try {
            userRepository.save(student);
            redirectAttributes.addFlashAttribute("successMessage", "Student details updated.");
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An account already exists with that username.");
        }
        return "redirect:/admin/students";
    }

    @PostMapping("/student/deactivate/{id}")
    public String deactivateStudent(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return setStudentActive(id, false, redirectAttributes);
    }

    @PostMapping("/student/activate/{id}")
    public String activateStudent(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return setStudentActive(id, true, redirectAttributes);
    }

    private String setStudentActive(Long id, boolean active, RedirectAttributes redirectAttributes) {
        User student = userRepository.findById(id)
                .filter(user -> "ROLE_STUDENT".equals(user.getRole()))
                .orElse(null);
        if (student == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Student account was not found.");
            return "redirect:/admin/students";
        }

        student.setActive(active);
        userRepository.save(student);
        String status = active ? "activated" : "deactivated";
        redirectAttributes.addFlashAttribute("successMessage",
                "Student account " + status + ". Existing exam attempts and results were preserved.");
        return "redirect:/admin/students";
    }


    @PostMapping("/student/reset-password")
    public String resetStudentPassword(@RequestParam("userId") Long userId,
                                       @RequestParam("newPassword") String newPassword,
                                       RedirectAttributes redirectAttributes) {

        if (newPassword.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Password cannot be empty.");
            return "redirect:/admin/students";
        }

        try {
            User student = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid student Id:" + userId));

            // Encode the new password before saving
            student.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(student);

            redirectAttributes.addFlashAttribute("successMessage", "Password for " + student.getUsername() + " has been reset.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while trying to reset the password.");
        }
        return "redirect:/admin/students";
    }
}

