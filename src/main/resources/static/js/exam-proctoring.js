import { FilesetResolver, FaceLandmarker } from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision/vision_bundle.mjs";
import { env, pipeline } from "https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.7.2";

const LOOKING_AWAY_LIMIT = 8;
const DEVICE_LIMIT = 2;
const configElement = document.getElementById("webcam-monitor-panel");
const config = {
    faceMissingMs: Number(configElement.dataset.faceMissingMs),
    faceTurnedMs: Number(configElement.dataset.faceTurnedMs),
    faceYawThreshold: Number(configElement.dataset.faceYawThreshold),
    faceConfidence: Number(configElement.dataset.faceConfidence),
    objectConfidence: Number(configElement.dataset.objectConfidence),
    incidentCooldownMs: Number(configElement.dataset.incidentCooldownMs),
    objectScanIntervalMs: Number(configElement.dataset.objectScanIntervalMs),
    objectConfirmationScans: Number(configElement.dataset.objectConfirmationScans),
    objectAbsenceScans: Number(configElement.dataset.objectAbsenceScans),
    normalRearmMs: Number(configElement.dataset.normalRearmMs)
};

env.allowLocalModels = false;
env.useBrowserCache = true;

const video = document.getElementById("webcam-preview");
const monitorPanel = document.getElementById("webcam-monitor-panel");
const warningMessage = document.getElementById("proctoring-alert");
const examForm = document.getElementById("exam-form");
let stream;
let faceLandmarker;
let objectDetector;
let activeExamId;
let monitoring = false;
let detectionTimer;
let faceCondition;
let faceConditionSince = 0;
let normalSince = 0;
let lookAwayReportedForCondition = false;
let lastLookAwayViolationAt = 0;
let deviceIncidentActive = false;
let deviceAbsentScans = 0;
let deviceCandidateType;
let deviceCandidateHits = 0;
let lastDeviceViolationAt = 0;
let lastObjectScanAt = 0;
let detectionInProgress = false;
let answerSync = Promise.resolve();

examForm.addEventListener("change", (event) => {
    if (!monitoring || !(event.target instanceof HTMLInputElement) || event.target.type !== "radio") return;
    const questionId = event.target.name.match(/^q_(\d+)$/)?.[1];
    if (!questionId) return;
    answerSync = answerSync.catch(() => {}).then(async () => {
        const response = await fetch("/exam/answer", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                examId: activeExamId,
                questionId,
                selectedOption: event.target.value
            })
        });
        if (!response.ok) throw new Error("Could not save your selected answer.");
    });
    answerSync.catch(() => {});
});

window.examProctoringReady = () => Boolean(stream && faceLandmarker && objectDetector);

window.initializeExamProctoring = async (examId) => {
    activeExamId = examId;
    try {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            throw new Error("This browser cannot access a webcam. Use HTTPS or localhost and a supported browser.");
        }

        stream = await navigator.mediaDevices.getUserMedia({
            audio: false,
            video: { facingMode: "user", width: { ideal: 640 }, height: { ideal: 480 } }
        });
        video.srcObject = stream;
        await video.play();
        video.style.display = "block";
        if (!video.videoWidth || !video.videoHeight) {
            throw new Error("The camera did not provide a usable video feed.");
        }

        const visionFiles = await FilesetResolver.forVisionTasks(
            "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision/wasm"
        );
        faceLandmarker = await FaceLandmarker.createFromOptions(visionFiles, {
            baseOptions: {
                modelAssetPath: "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
            },
            runningMode: "VIDEO",
            numFaces: 1,
            minFaceDetectionConfidence: config.faceConfidence,
            minFacePresenceConfidence: config.faceConfidence,
            minTrackingConfidence: config.faceConfidence,
            outputFaceBlendshapes: false,
            outputFacialTransformationMatrixes: true
        });
        objectDetector = await pipeline("zero-shot-object-detection", "Xenova/owlvit-base-patch32");

        for (const track of stream.getVideoTracks()) {
            track.addEventListener("ended", onCameraDisconnected);
        }
        return true;
    } catch (error) {
        releaseCamera();
        faceLandmarker = undefined;
        objectDetector = undefined;
        throw new Error(error.message || "Camera or local AI model initialization failed. The exam cannot start.");
    }
};

window.startExamProctoring = () => {
    if (!window.examProctoringReady()) {
        throw new Error("Webcam monitoring is not initialized.");
    }
    monitoring = true;
    detectionTimer = window.setInterval(runDetection, 300);
};

window.stopExamProctoring = () => {
    monitoring = false;
    if (detectionTimer) window.clearInterval(detectionTimer);
    detectionTimer = undefined;
    releaseCamera();
};

function releaseCamera() {
    if (stream) {
        for (const track of stream.getTracks()) track.stop();
    }
    stream = undefined;
    if (video) {
        video.srcObject = null;
        video.style.display = "none";
    }
}

function onCameraDisconnected() {
    if (monitoring) void failClosed("The webcam connection was lost during the exam.");
}

async function runDetection() {
    if (!monitoring || detectionInProgress || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
    detectionInProgress = true;
    try {
        const now = performance.now();
        const result = faceLandmarker.detectForVideo(video, now);
        updateFaceCondition(result.faceLandmarks?.[0], now);
        if (Date.now() - lastObjectScanAt >= config.objectScanIntervalMs) {
            lastObjectScanAt = Date.now();
            await scanForDevices();
        }
    } catch (error) {
        await failClosed("Local AI detection stopped: " + (error.message || "unknown model error"));
    } finally {
        detectionInProgress = false;
    }
}

function updateFaceCondition(landmarks, now) {
    let condition = "normal";
    if (!landmarks) {
        condition = "missing";
    } else {
        const leftEye = landmarks[33];
        const rightEye = landmarks[263];
        const nose = landmarks[1];
        const eyeWidth = Math.abs(rightEye.x - leftEye.x);
        const eyeMidpoint = (leftEye.x + rightEye.x) / 2;
        const normalizedNoseOffset = eyeWidth > 0 ? Math.abs(nose.x - eyeMidpoint) / eyeWidth : 0;
        if (normalizedNoseOffset > config.faceYawThreshold) condition = "turned";
    }

    if (condition !== faceCondition) {
        faceCondition = condition;
        faceConditionSince = now;
        if (condition === "normal") normalSince = now;
        else normalSince = 0;
        lookAwayReportedForCondition = false;
    }

    if (condition === "normal") {
        if (!normalSince) normalSince = now;
        if (now - normalSince >= config.normalRearmMs) lookAwayReportedForCondition = false;
        return;
    }

    const requiredDuration = condition === "missing" ? config.faceMissingMs : config.faceTurnedMs;
    if (!lookAwayReportedForCondition && now - faceConditionSince >= requiredDuration
        && Date.now() - lastLookAwayViolationAt >= config.incidentCooldownMs) {
        lookAwayReportedForCondition = true;
        lastLookAwayViolationAt = Date.now();
        void reportViolation("looking_away");
    }
}

async function scanForDevices() {
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext("2d", { willReadFrequently: false }).drawImage(video, 0, 0, canvas.width, canvas.height);
    const detections = await objectDetector(canvas, ["a mobile phone", "earbuds", "earphones"]);
    const detected = detections
        .filter((item) => item.score >= config.objectConfidence)
        .map((item) => ({
            type: item.label.toLowerCase().includes("ear") ? "earphones" : "mobile_phone",
            score: item.score
        }))
        .sort((first, second) => second.score - first.score)[0];

    if (!detected) {
        deviceAbsentScans++;
        deviceCandidateType = undefined;
        deviceCandidateHits = 0;
        if (deviceAbsentScans >= config.objectAbsenceScans) deviceIncidentActive = false;
        return;
    }

    deviceAbsentScans = 0;
    if (deviceIncidentActive) return;
    if (deviceCandidateType === detected.type) {
        deviceCandidateHits++;
    } else {
        deviceCandidateType = detected.type;
        deviceCandidateHits = 1;
    }

    if (deviceCandidateHits >= config.objectConfirmationScans
        && Date.now() - lastDeviceViolationAt >= config.incidentCooldownMs) {
        deviceIncidentActive = true;
        lastDeviceViolationAt = Date.now();
        void reportViolation("prohibited_device", detected.type);
    }
}

async function reportViolation(category, objectType) {
    try {
        await answerSync;
        const response = await fetch("/exam/proctoring/violation", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ examId: activeExamId, category, objectType })
        });
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.error || "Could not save the proctoring violation.");
        if (!payload.counted) return;

        if (category === "looking_away") {
            document.getElementById("looking-warning-count").textContent =
                payload.warningCount + "/" + LOOKING_AWAY_LIMIT;
            warningMessage.textContent = payload.warningCount === LOOKING_AWAY_LIMIT - 1
                ? "Warning " + payload.warningCount + "/8: One more violation will terminate your examination."
                : "Warning " + payload.warningCount + "/8: Please look at your examination screen.";
        } else {
            document.getElementById("device-warning-count").textContent =
                payload.warningCount + "/" + DEVICE_LIMIT;
            const detectionMessage = objectType === "mobile_phone"
                ? "A mobile phone has been detected."
                : "Earphones or earbuds have been detected.";
            warningMessage.textContent = "Warning " + payload.warningCount + "/2: " + detectionMessage
                + " Please remove them immediately.";
        }

        if (payload.terminated) {
            warningMessage.textContent = category === "looking_away"
                ? "Maximum looking-away violations reached. Your examination has been terminated."
                : "Second prohibited-device violation detected. Your examination has been terminated.";
            window.terminateExamForProctoring(payload.terminationReason);
        }
    } catch (error) {
        await failClosed("A proctoring event could not be saved: " + error.message);
    }
}

async function failClosed(reason) {
    if (!monitoring) return;
    monitoring = false;
    if (detectionTimer) window.clearInterval(detectionTimer);
    detectionTimer = undefined;
    releaseCamera();
    warningMessage.textContent = reason;
    try {
        await answerSync.catch(() => {});
        await fetch("/exam/proctoring/failure", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ examId: activeExamId, reason })
        });
    } finally {
        window.terminateExamForProctoring("Webcam monitoring failed. " + reason);
    }
}
