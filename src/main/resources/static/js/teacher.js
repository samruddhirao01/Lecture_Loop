let mediaRecorder;
let recordedChunks = [];
let mySections = [];
let screenStream, micStream;
let recordTimerInterval, recordSeconds = 0;

let currentLectureId = null;
let currentLectureDoubts = [];
let quizOptionCount = 0;
let pollOptionCount = 0;

async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    window.location.href = 'index.html';
}

async function loadSections() {
    const select = document.getElementById('sectionSelect');
    try {
        mySections = await apiFetch('/api/teacher/sections');
    } catch (err) {
        select.innerHTML = `<option value="">Failed to load sections</option>`;
        return;
    }
    if (mySections.length === 0) {
        select.innerHTML = '<option value="">No sections assigned yet</option>';
        return;
    }
    select.innerHTML = mySections.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
}

/* ---------------- Recording ---------------- */

async function startRecording() {
    const errorEl = document.getElementById('recordError');
    errorEl.textContent = '';

    if (!document.getElementById('lectureTitle').value.trim()) {
        errorEl.textContent = 'Enter a lecture title first.';
        return;
    }

    try {
        // Mic requested first (right after the click) so the browser doesn't
        // lose the "user activation" window while the screen-picker dialog is open.
        micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        screenStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });

        const combinedStream = new MediaStream([
            ...screenStream.getVideoTracks(),
            ...micStream.getAudioTracks()
        ]);

        recordedChunks = [];
        mediaRecorder = new MediaRecorder(combinedStream, { mimeType: 'video/webm; codecs=vp9,opus' });

        mediaRecorder.ondataavailable = e => {
            if (e.data.size > 0) recordedChunks.push(e.data);
        };

        mediaRecorder.onstop = async () => {
            screenStream.getTracks().forEach(track => track.stop());
            micStream.getTracks().forEach(track => track.stop());
            stopTimer();
            await uploadRecording();
        };

        mediaRecorder.start();
        document.getElementById('startBtn').disabled = true;
        document.getElementById('stopBtn').disabled = false;
        startTimer();

    } catch (e) {
        console.error(e);
        errorEl.textContent = 'Could not start recording: ' + e.message;
    }
}

function stopRecording() {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
    }
    // Start stays disabled until the upload inside mediaRecorder.onstop
    // actually finishes (success or failure). Re-enabling it here would let
    // the user launch a new recording while the old streams are still
    // tearing down and the old upload is still in flight.
    document.getElementById('stopBtn').disabled = true;
}

function startTimer() {
    recordSeconds = 0;
    const el = document.getElementById('recordTimer');
    el.textContent = '00:00';
    recordTimerInterval = setInterval(() => {
        recordSeconds++;
        const m = Math.floor(recordSeconds / 60).toString().padStart(2, '0');
        const s = (recordSeconds % 60).toString().padStart(2, '0');
        el.textContent = `${m}:${s}`;
    }, 1000);
}

function stopTimer() {
    clearInterval(recordTimerInterval);
    document.getElementById('recordTimer').textContent = '';
}

async function uploadRecording() {
    const statusEl = document.getElementById('uploadStatus');
    const startBtn = document.getElementById('startBtn');
    const stopBtn = document.getElementById('stopBtn');
    startBtn.disabled = true;
    stopBtn.disabled = true;
    statusEl.textContent = 'Uploading and saving lecture, please wait...';

    const blob = new Blob(recordedChunks, { type: 'video/webm' });
    const preview = document.getElementById('preview');
    preview.src = URL.createObjectURL(blob);
    preview.style.display = 'block';

    const formData = new FormData();
    formData.append('file', blob, 'lecture.webm');
    formData.append('title', document.getElementById('lectureTitle').value.trim());
    formData.append('sectionId', document.getElementById('sectionSelect').value);

    try {
        const data = await apiFetch('/api/teacher/lectures/upload', { method: 'POST', body: formData });
        statusEl.textContent = `Saved automatically to ${data.section} - students can watch it now.`;
        document.getElementById('lectureTitle').value = '';
        await loadLectures();
    } catch (err) {
        statusEl.textContent = 'Upload failed: ' + err.message + ' - please try recording again.';
    } finally {
        // Only now is it safe to let the teacher start a new recording.
        startBtn.disabled = false;
    }
}

/* ---------------- Lecture list ---------------- */

async function loadLectures() {
    const tbody = document.querySelector('#lecturesTable tbody');
    tbody.innerHTML = '<tr><td colspan="5" class="empty-note">Loading...</td></tr>';

    let lectures;
    try {
        lectures = await apiFetch('/api/teacher/lectures');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="empty-note">Failed to load lectures: ${err.message}</td></tr>`;
        return;
    }

    tbody.innerHTML = '';
    if (lectures.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="empty-note">No lectures uploaded yet.</td></tr>';
        return;
    }
    lectures.forEach(l => {
        const tr = document.createElement('tr');
        const when = new Date(l.uploadedAt).toLocaleString();
        tr.innerHTML = `
            <td>${l.title}</td>
            <td>${l.section}</td>
            <td>${when}</td>
            <td>${l.doubtCount}</td>
            <td>
                <button class="secondary" onclick="openWorkspace(${l.id}, '${escapeQuotes(l.title)}', '${l.videoUrl}')">Manage</button>
                <button class="danger" onclick="deleteLecture(${l.id})">Delete</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function deleteLecture(id) {
    if (!confirm('Delete this recording permanently? This cannot be undone.')) return;

    try {
        await apiFetch(`/api/teacher/lectures/${id}`, { method: 'DELETE' });
    } catch (err) {
        alert(err.message);
        return;
    }

    if (currentLectureId === id) {
        document.getElementById('workspaceCard').style.display = 'none';
        currentLectureId = null;
    }
    await loadLectures();
}

function escapeQuotes(str) {
    return str.replace(/'/g, "\\'");
}

/* ---------------- Workspace (doubts + heatmap + quiz + poll) ---------------- */

async function openWorkspace(lectureId, title, videoUrl) {
    currentLectureId = lectureId;
    document.getElementById('workspaceCard').style.display = 'block';
    document.getElementById('workspaceLectureTitle').textContent = title;

    const player = document.getElementById('workspacePlayer');
    player.src = videoUrl;
    // Duration isn't known immediately after setting src -- re-render once
    // the browser has actually read the video's real length.
    player.onloadedmetadata = () => renderCurrentHeatmap();

    resetQuizForm();
    resetPollForm();

    await loadDoubtsAndHeatmap();
    await loadQuizzes();
    await loadPolls();

    window.scrollTo({ top: document.getElementById('workspaceCard').offsetTop, behavior: 'smooth' });
}

function renderCurrentHeatmap() {
    const player = document.getElementById('workspacePlayer');
    const duration = (player && isFinite(player.duration) && player.duration > 0) ? player.duration : null;
    renderHeatmapCurve('heatmapBar', currentLectureDoubts, duration, (seconds) => {
        player.currentTime = seconds;
        player.play();
    });
}

async function loadDoubtsAndHeatmap() {
    const list = document.getElementById('doubtsList');
    list.innerHTML = '<p class="empty-note">Loading...</p>';

    try {
        currentLectureDoubts = await apiFetch(`/api/teacher/lectures/${currentLectureId}/doubts`);
    } catch (err) {
        list.innerHTML = `<p class="empty-note">Failed to load doubts: ${err.message}</p>`;
        currentLectureDoubts = [];
        renderCurrentHeatmap();
        return;
    }

    renderCurrentHeatmap();

    if (currentLectureDoubts.length === 0) {
        list.innerHTML = '<p class="empty-note">No doubts posted on this lecture yet.</p>';
        return;
    }

    list.innerHTML = currentLectureDoubts.map(d => `
        <div class="doubt-item">
            <div class="meta clickable" onclick="seekWorkspaceTo(${d.timestampSeconds})">
                ${d.anonId} &middot; at ${formatTime(d.timestampSeconds)} (click to jump)
                <span style="color:var(--ink-faint); text-transform:none; font-weight:400;">(resolves to: ${d.realFullName})</span>
            </div>
            <div>${d.questionText}</div>
            ${d.teacherReply
                ? `<div class="reply-box">Your reply: ${d.teacherReply}</div>`
                : `<div style="margin-top:8px;">
                       <textarea id="reply-${d.id}" placeholder="Type a reply..." rows="2"></textarea>
                       <button class="primary" style="padding:6px 14px; font-size:12px;" onclick="submitReply(${d.id})">Reply</button>
                   </div>`
            }
        </div>
    `).join('');
}

function seekWorkspaceTo(seconds) {
    const player = document.getElementById('workspacePlayer');
    player.currentTime = seconds;
    player.play();
}

async function submitReply(doubtId) {
    const textarea = document.getElementById(`reply-${doubtId}`);
    const reply = textarea.value.trim();
    if (!reply) return;

    try {
        await apiFetch(`/api/doubts/${doubtId}/reply`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reply })
        });
    } catch (err) {
        alert(err.message);
        return;
    }
    await loadDoubtsAndHeatmap();
}

function formatTime(seconds) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
}

/* ---------------- Quiz ---------------- */

function resetQuizForm() {
    document.getElementById('quizQuestion').value = '';
    document.getElementById('quizOptionsInputs').innerHTML = '';
    document.getElementById('quizError').textContent = '';
    quizOptionCount = 0;
    addQuizOptionInput();
    addQuizOptionInput();
}

function addQuizOptionInput() {
    if (quizOptionCount >= 4) return;
    quizOptionCount++;
    const wrap = document.getElementById('quizOptionsInputs');
    const div = document.createElement('div');
    div.style.display = 'flex';
    div.style.gap = '8px';
    div.style.alignItems = 'center';
    div.style.marginBottom = '6px';
    div.innerHTML = `
        <input type="radio" name="quizCorrect" value="${quizOptionCount - 1}" style="width:auto;">
        <input type="text" class="quiz-option-text" placeholder="Option text">
    `;
    wrap.appendChild(div);
}

async function createQuiz() {
    const errorEl = document.getElementById('quizError');
    errorEl.textContent = '';

    const question = document.getElementById('quizQuestion').value.trim();
    const optionInputs = document.querySelectorAll('.quiz-option-text');
    const correctRadio = document.querySelector('input[name="quizCorrect"]:checked');

    if (!question) { errorEl.textContent = 'Enter a question.'; return; }
    if (!correctRadio) { errorEl.textContent = 'Select the correct option.'; return; }

    const options = Array.from(optionInputs).map((input, idx) => ({
        text: input.value.trim(),
        correct: idx === Number(correctRadio.value)
    }));

    if (options.some(o => !o.text)) { errorEl.textContent = 'Fill in all option text fields.'; return; }

    try {
        await apiFetch('/api/quizzes', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ lectureId: currentLectureId, question, options })
        });
    } catch (err) {
        errorEl.textContent = err.message;
        return;
    }

    resetQuizForm();
    await loadQuizzes();
}

async function loadQuizzes() {
    const list = document.getElementById('quizList');
    list.innerHTML = '<p class="empty-note">Loading...</p>';

    let quizzes;
    try {
        quizzes = await apiFetch(`/api/quizzes?lectureId=${currentLectureId}`);
    } catch (err) {
        list.innerHTML = `<p class="empty-note">Failed to load quizzes: ${err.message}</p>`;
        return;
    }

    if (quizzes.length === 0) {
        list.innerHTML = '<p class="empty-note">No quizzes created for this lecture yet.</p>';
        return;
    }

    list.innerHTML = quizzes.map(q => `
        <div class="quiz-card">
            <div class="question">${q.question}</div>
            ${q.options.map(o => `<div class="option-row ${o.correct ? 'correct' : ''}" style="cursor:default;">${o.text}${o.correct ? ' &#10003; correct' : ''}</div>`).join('')}
            <button class="secondary" style="margin-top:8px;" onclick="viewQuizResults(${q.id})">View Results</button>
            <div id="quiz-results-${q.id}" style="margin-top:8px;"></div>
        </div>
    `).join('');
}

async function viewQuizResults(quizId) {
    const el = document.getElementById(`quiz-results-${quizId}`);
    el.innerHTML = '<div class="empty-note">Loading...</div>';

    let data;
    try {
        data = await apiFetch(`/api/quizzes/${quizId}/results`);
    } catch (err) {
        el.innerHTML = `<div class="empty-note">Failed to load results: ${err.message}</div>`;
        return;
    }

    if (data.totalAnswered === 0) {
        el.innerHTML = '<div class="empty-note">No attempts yet.</div>';
        return;
    }

    el.innerHTML = `
        <div style="font-size:12.5px; font-weight:700; margin-bottom:6px;">
            ${data.totalCorrect} / ${data.totalAnswered} answered correctly
        </div>
        <table>
            <thead><tr><th>Student</th><th>Selected</th><th>Result</th></tr></thead>
            <tbody>
                ${data.students.map(s => `
                    <tr>
                        <td>${s.studentFullName} (${s.studentUsername})</td>
                        <td>${s.selectedOption}</td>
                        <td>${s.correct ? '&#10003; Correct' : '&#10007; Incorrect'}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;
}

/* ---------------- Poll ---------------- */

function resetPollForm() {
    document.getElementById('pollQuestion').value = '';
    document.getElementById('pollOptionsInputs').innerHTML = '';
    document.getElementById('pollError').textContent = '';
    pollOptionCount = 0;
    addPollOptionInput();
    addPollOptionInput();
}

function addPollOptionInput() {
    if (pollOptionCount >= 4) return;
    pollOptionCount++;
    const wrap = document.getElementById('pollOptionsInputs');
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'poll-option-text';
    input.placeholder = 'Option text';
    input.style.marginBottom = '6px';
    wrap.appendChild(input);
}

async function createPoll() {
    const errorEl = document.getElementById('pollError');
    errorEl.textContent = '';

    const question = document.getElementById('pollQuestion').value.trim();
    const optionInputs = document.querySelectorAll('.poll-option-text');
    const options = Array.from(optionInputs).map(input => input.value.trim());

    if (!question) { errorEl.textContent = 'Enter a question.'; return; }
    if (options.some(o => !o)) { errorEl.textContent = 'Fill in all option text fields.'; return; }

    try {
        await apiFetch('/api/polls', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ lectureId: currentLectureId, question, options })
        });
    } catch (err) {
        errorEl.textContent = err.message;
        return;
    }

    resetPollForm();
    await loadPolls();
}

async function loadPolls() {
    const list = document.getElementById('pollList');
    list.innerHTML = '<p class="empty-note">Loading...</p>';

    let polls;
    try {
        polls = await apiFetch(`/api/polls?lectureId=${currentLectureId}`);
    } catch (err) {
        list.innerHTML = `<p class="empty-note">Failed to load polls: ${err.message}</p>`;
        return;
    }

    if (polls.length === 0) {
        list.innerHTML = '<p class="empty-note">No polls created for this lecture yet.</p>';
        return;
    }

    list.innerHTML = polls.map(p => `
        <div class="poll-card">
            <div class="question">${p.question} <span style="font-weight:400; color:var(--ink-faint); font-size:12px;">(${p.totalVotes} votes)</span></div>
            ${p.options.map(o => {
                const pct = p.totalVotes > 0 ? Math.round((o.voteCount / p.totalVotes) * 100) : 0;
                return `
                    <div style="margin-bottom:8px;">
                        <div style="display:flex; justify-content:space-between; font-size:12.5px;">
                            <span>${o.text}</span><span>${o.voteCount} (${pct}%)</span>
                        </div>
                        <div class="poll-bar-track"><div class="poll-bar-fill" style="width:${pct}%;"></div></div>
                    </div>
                `;
            }).join('')}
        </div>
    `).join('');
}

(async function init() {
    if (!(await guard('TEACHER'))) return;
    await loadSections();
    await loadLectures();
})();
