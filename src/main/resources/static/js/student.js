let currentLectureId = null;
let currentDoubtsForHeatmap = [];

async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    window.location.href = 'index.html';
}

async function loadLectures() {
    const tbody = document.querySelector('#lecturesTable tbody');
    tbody.innerHTML = '<tr><td colspan="3" class="empty-note">Loading...</td></tr>';

    let lectures;
    try {
        lectures = await apiFetch('/api/student/lectures');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="3" class="empty-note">Failed to load lectures: ${err.message}</td></tr>`;
        return;
    }

    tbody.innerHTML = '';
    if (lectures.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" class="empty-note">No lectures uploaded for your section yet.</td></tr>';
        return;
    }

    lectures.forEach(l => {
        const tr = document.createElement('tr');
        const when = new Date(l.uploadedAt).toLocaleString();
        tr.innerHTML = `
            <td>${l.title}</td>
            <td>${when}</td>
            <td><button class="secondary" onclick="openLecture(${l.id}, '${l.title.replace(/'/g, "\\'")}', '${l.videoUrl}')">Watch</button></td>
        `;
        tbody.appendChild(tr);
    });
}

async function openLecture(id, title, videoUrl) {
    currentLectureId = id;
    document.getElementById('playerCard').style.display = 'block';
    document.getElementById('playerTitle').textContent = title;

    const player = document.getElementById('player');
    player.src = videoUrl;
    // Duration isn't known immediately after setting src -- re-render once
    // the browser has actually read the video's real length.
    player.onloadedmetadata = () => renderCurrentHeatmap();

    document.getElementById('doubtText').value = '';
    document.getElementById('bookmarkNote').value = '';

    await loadDoubtsAndHeatmap();
    await loadBookmarks();
    await loadQuizzes();
    await loadPolls();

    window.scrollTo({ top: document.getElementById('playerCard').offsetTop, behavior: 'smooth' });
}

function renderCurrentHeatmap() {
    const player = document.getElementById('player');
    const duration = (player && isFinite(player.duration) && player.duration > 0) ? player.duration : null;
    renderHeatmapCurve('heatmapBar', currentDoubtsForHeatmap, duration, (seconds) => seekTo(seconds));
}

/* ---------------- Doubts + heatmap ---------------- */

async function postDoubt() {
    const text = document.getElementById('doubtText').value.trim();
    if (!text) return;

    const player = document.getElementById('player');
    const timestampSeconds = Math.floor(player.currentTime);

    try {
        await apiFetch('/api/doubts', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ lectureId: currentLectureId, timestampSeconds, questionText: text })
        });
    } catch (err) {
        alert(err.message);
        return;
    }

    document.getElementById('doubtText').value = '';
    await loadDoubtsAndHeatmap();
}

async function loadDoubtsAndHeatmap() {
    const list = document.getElementById('doubtsList');
    list.innerHTML = '<p class="empty-note">Loading...</p>';

    try {
        currentDoubtsForHeatmap = await apiFetch(`/api/student/lectures/${currentLectureId}/doubts`);
    } catch (err) {
        list.innerHTML = `<p class="empty-note">Failed to load doubts: ${err.message}</p>`;
        currentDoubtsForHeatmap = [];
        renderCurrentHeatmap();
        return;
    }

    renderCurrentHeatmap();

    if (currentDoubtsForHeatmap.length === 0) {
        list.innerHTML = '<p class="empty-note">No doubts posted yet. Be the first to ask.</p>';
        return;
    }

    list.innerHTML = currentDoubtsForHeatmap.map(d => `
        <div class="doubt-item">
            <div class="meta clickable" onclick="seekTo(${d.timestampSeconds})">${d.anonId} &middot; at ${formatTime(d.timestampSeconds)} (click to jump)</div>
            <div>${d.questionText}</div>
            ${d.teacherReply
                ? `<div class="reply-box">Teacher's reply: ${d.teacherReply}</div>`
                : `<div style="font-size:12px; color:var(--ink-faint); margin-top:4px;">Waiting for teacher's reply...</div>`
            }
        </div>
    `).join('');
}

function seekTo(seconds) {
    const player = document.getElementById('player');
    player.currentTime = seconds;
    player.play();
}

function formatTime(seconds) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
}

/* ---------------- Bookmarks ---------------- */

async function addBookmark() {
    const player = document.getElementById('player');
    const timestampSeconds = Math.floor(player.currentTime);
    const note = document.getElementById('bookmarkNote').value.trim();

    try {
        await apiFetch('/api/bookmarks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ lectureId: currentLectureId, timestampSeconds, note })
        });
    } catch (err) {
        alert(err.message);
        return;
    }

    document.getElementById('bookmarkNote').value = '';
    await loadBookmarks();
}

async function loadBookmarks() {
    const list = document.getElementById('bookmarksList');
    list.innerHTML = '<p class="empty-note">Loading...</p>';

    let bookmarks;
    try {
        bookmarks = await apiFetch(`/api/bookmarks?lectureId=${currentLectureId}`);
    } catch (err) {
        list.innerHTML = `<p class="empty-note">Failed to load bookmarks: ${err.message}</p>`;
        return;
    }

    if (bookmarks.length === 0) {
        list.innerHTML = '<p class="empty-note">No bookmarks yet - add one at any point while watching.</p>';
        return;
    }

    list.innerHTML = bookmarks.map(b => `
        <div class="bookmark-item">
            <div>
                <span class="time" onclick="seekTo(${b.timestampSeconds})">${formatTime(b.timestampSeconds)}</span>
                ${b.note ? b.note : '<span style="color:var(--ink-faint);">(no note)</span>'}
            </div>
            <button class="danger" onclick="deleteBookmark(${b.id})">Delete</button>
        </div>
    `).join('');
}

async function deleteBookmark(id) {
    try {
        await apiFetch(`/api/bookmarks/${id}`, { method: 'DELETE' });
    } catch (err) {
        alert(err.message);
        return;
    }
    await loadBookmarks();
}

/* ---------------- Quiz ---------------- */

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
        list.innerHTML = '<p class="empty-note">No quizzes posted for this lecture yet.</p>';
        return;
    }

    list.innerHTML = quizzes.map(q => `
        <div class="quiz-card">
            <div class="question">${q.question}</div>
            ${q.options.map(o => {
                let cls = 'option-row';
                if (q.alreadyAttempted) {
                    if (o.correct) cls += ' correct';
                    else if (o.id === q.yourSelectedOptionId) cls += ' incorrect';
                    return `<div class="${cls}">${o.text} ${o.id === q.yourSelectedOptionId ? '(your answer)' : ''}</div>`;
                }
                return `<div class="${cls}" onclick="submitQuizAttempt(${q.id}, ${o.id})">${o.text}</div>`;
            }).join('')}
            ${q.alreadyAttempted ? '<div style="font-size:12px; color:var(--ink-faint); margin-top:6px;">You already answered this quiz.</div>' : ''}
        </div>
    `).join('');
}

async function submitQuizAttempt(quizId, optionId) {
    try {
        await apiFetch(`/api/quizzes/${quizId}/attempt`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ optionId })
        });
    } catch (err) {
        alert(err.message);
        return;
    }
    await loadQuizzes();
}

/* ---------------- Poll ---------------- */

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
        list.innerHTML = '<p class="empty-note">No polls posted for this lecture yet.</p>';
        return;
    }

    list.innerHTML = polls.map(p => {
        if (p.alreadyVoted) {
            return `
                <div class="poll-card">
                    <div class="question">${p.question} <span style="font-weight:400; color:var(--ink-faint); font-size:12px;">(${p.totalVotes} votes so far)</span></div>
                    ${p.options.map(o => {
                        const pct = p.totalVotes > 0 ? Math.round((o.voteCount / p.totalVotes) * 100) : 0;
                        return `
                            <div style="margin-bottom:8px;">
                                <div style="display:flex; justify-content:space-between; font-size:12.5px;">
                                    <span>${o.text} ${o.id === p.yourOptionId ? '(your vote)' : ''}</span><span>${pct}%</span>
                                </div>
                                <div class="poll-bar-track"><div class="poll-bar-fill" style="width:${pct}%;"></div></div>
                            </div>
                        `;
                    }).join('')}
                </div>
            `;
        }
        return `
            <div class="poll-card">
                <div class="question">${p.question}</div>
                ${p.options.map(o => `<div class="option-row" onclick="submitVote(${p.id}, ${o.id})">${o.text}</div>`).join('')}
            </div>
        `;
    }).join('');
}

async function submitVote(pollId, optionId) {
    try {
        await apiFetch(`/api/polls/${pollId}/vote`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ optionId })
        });
    } catch (err) {
        alert(err.message);
        return;
    }
    await loadPolls();
}

(async function init() {
    if (!(await guard('STUDENT'))) return;
    await loadLectures();
})();
