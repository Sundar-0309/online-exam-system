# 🚀 Online Exam System – Java (Spring Boot)

<p align="center">
<img src="https://img.shields.io/badge/Java-17-blue?style=for-the-badge&logo=java&logoColor=white" alt="Java 17">
<img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.x">
<img src="https://img.shields.io/badge/JPA%20%2F%20Hibernate-red?style=for-the-badge" alt="JPA / Hibernate">
<img src="https://img.shields.io/badge/H2%20Database-lightgrey?style=for-the-badge" alt="H2 Database">
</p>

A comprehensive **Online Examination System** built using **Spring Boot, Spring Security, Thymeleaf, Bootstrap 5**, and **JPA/Hibernate**.  
The platform provides a secure and user-friendly environment for **Admins** and **Students** to manage and take online tests effectively.

✔️ Completely Free  
✔️ Full Source Code Included

---

# 💖 Support & Contact

This project is created by **LazyCoder**.

### 📺 Subscribe to My YouTube Channel
I post tutorials, Java/Python projects, and Spring Boot content regularly.

👉 **Subscribe Here:**  
https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1

---

### 🤝 Need Help or Want a Custom Project?

📌 Facing issues with this project?  
📌 Want a custom Java/Spring Boot/Django project?

**I am available for freelance development.**

📱 **WhatsApp:** https://wa.me/919572181024

<p align="center">
<a href="https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1">
<img src="https://img.shields.io/badge/Subscribe-LazyCoder-red?style=for-the-badge&logo=youtube" >
</a>

<a href="https://wa.me/919572181024">
<img src="https://img.shields.io/badge/WhatsApp-Chat%20Now-green?style=for-the-badge&logo=whatsapp" >
</a>
</p>

---

# 📸 Screenshots


<table width="100%">


<tr>
<td align="center"><b>Exam Page (with Pagination)</b></td>
</tr>
<tr>
<td align="center"><img src="https://github.com/sumitkumar1503/online-exam-system/blob/master/screenshots/exampage.png" width="90%"></td>
</tr>

<tr>
<td align="center"><b>Admin Dashboard</b></td>
</tr>
<tr>
<td align="center"><img src="https://github.com/sumitkumar1503/online-exam-system/blob/master/screenshots/admindashboard.png?raw=true" width="90%"></td>
</tr>

<tr>
<td align="center"><b>Manage Exam</b></td>
</tr>
<tr>
<td align="center"><img src="https://github.com/sumitkumar1503/online-exam-system/blob/master/screenshots/adminmanageexam.png" width="90%"></td>
</tr>

<tr>
<td align="center"><b>Manage Question</b></td>
</tr>
<tr>
<td align="center"><img src="https://github.com/sumitkumar1503/online-exam-system/blob/master/screenshots/adminmanagequestion.png" width="90%"></td>
</tr>
</table>

---

# ✨ Features

## 👨‍💻 Admin Features
- Secure Admin Login
- Stats Dashboard (Total Students, Exams, Questions, Submissions)
- **Exam CRUD** (title, duration, description)
- **Question CRUD** per exam
- Cascade deletes for exams → questions → results
- Protect answered questions from accidental delete
- Manage Students
- Reset Student Password
- Delete Student Account (cascade all related data)
- View all submissions for any exam

---

## 🧑‍🎓 Student Features
- Student Registration (Full Name, Email, Mobile, Profile Picture)
- Secure Login
- Dashboard with KPIs + Performance Chart
- Take Exam (paginated interface + question palette)
- Live Timer (auto submit)
- Instant Results (score, percentage, pass/fail)
- Detailed Review Page (correct vs incorrect answers)
- Profile Update
- Upload New Profile Picture
- Change Password
- View All Previous Exam Results

## Webcam proctoring

Administrators can enable or disable **Webcam Monitoring** separately for each exam. It is disabled for new exams by default. Existing H2 records are retained and Hibernate adds the new exam and result metadata columns on startup.

When enabled, a student must grant camera access and wait for both local detection models to initialize before starting. The browser uses MediaPipe Face Landmarker for face presence and landmark-based head-turn estimation, and OWL-ViT through Transformers.js for open-vocabulary mobile-phone and earphone/earbud detection. Detection runs in the browser; webcam frames are not uploaded or recorded. The libraries and model assets are downloaded from jsDelivr and Hugging Face/Google on first use and cached by the browser. A network connection to those hosts is required for initial model loading. Earphones and small/partially hidden devices may not be detected reliably; this is not perfect cheating detection.

Looking-away incidents have an 8-warning limit. Phone and earphone/earbud incidents share a separate 2-warning device limit. Counts, detected object types, completion status, termination reason and timestamps are stored with the attempt and shown in Admin → exam Results. A confirmed model/camera failure during an enabled exam terminates and submits the attempt as a technical proctoring failure, without counting it as cheating.

Thresholds, confidence levels, scan cadence and cooldowns can be adjusted in `src/main/resources/application.properties` using the `proctoring.*` properties. The detection model itself and the warning limits remain fixed by the implementation.

`getUserMedia` is available only in a secure browser context: use `http://localhost:7890` for local testing, or serve the application over HTTPS for LAN/public access. Browsers will not grant webcam access to ordinary HTTP LAN-IP URLs. Configure TLS in Spring Boot or terminate TLS at a trusted HTTPS reverse proxy before distributing the exam URL.

---

# 🛠️ Tech Stack

| Layer | Technology                                 |
|------|--------------------------------------------|
| Backend | Spring Boot 3, Spring Security 6           |
| Frontend | Thymeleaf, Html, Bootstrap 5, Chart.js     |
| Database | H2 (file-based) (configurable to other DB) |
| ORM | Hibernate / JPA                            |
| Build | Maven                                      |
| Storage | Local File System for images               |

---

# 🚀 How to Run the Project

### ✔️ Prerequisites
- Java **17+**
- Maven
- Any IDE (IntelliJ, VS Code, Eclipse)

---

### ✔️ Clone the Repository

```bash
git clone https://github.com/sumitkumar1503/online-exam-system.git
cd online-exam-system
```

---

### ✔️ Start the Application

Run with Java 17 and Maven:

```powershell
.\mvnw.cmd spring-boot:run
```

Server will start at:

👉 http://localhost:7890

---

# 🗄️ Database (H2)

Access H2 Console:

👉 http://localhost:7890/h2-console

```
JDBC URL : jdbc:h2:file:./data/examdb  
Username : sa  
Password : password
```

---

# Admin Account Configuration

For production, set ADMIN_USERNAME and ADMIN_PASSWORD as environment variables before the first startup. Do not commit credentials.

---

# 📜 License

This project is **open-source** under the **MIT License**.

---

<p align="center">
<strong>Happy Coding ❤️</strong>
</p>
