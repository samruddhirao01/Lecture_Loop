let allSections = [];

async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    window.location.href = 'index.html';
}

async function loadSections() {
    const tbody = document.querySelector('#sectionsTable tbody');
    tbody.innerHTML = '<tr><td colspan="3" class="empty-note">Loading...</td></tr>';

    try {
        allSections = await apiFetch('/api/admin/sections');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="3" class="empty-note">Failed to load sections: ${err.message}</td></tr>`;
        return;
    }

    tbody.innerHTML = '';
    if (allSections.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" class="empty-note">No sections yet.</td></tr>';
    } else {
        allSections.forEach(s => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${s.name}</td>
                <td>${s.teacherNames.join(', ') || '-'}</td>
                <td><button class="danger" onclick="deleteSection(${s.id})">Delete</button></td>
            `;
            tbody.appendChild(tr);
        });
    }

    const select = document.getElementById('studentSection');
    select.innerHTML = allSections.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
}

async function createSection() {
    const name = document.getElementById('sectionName').value.trim();
    if (!name) return;

    try {
        await apiFetch('/api/admin/sections', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });
    } catch (err) {
        alert(err.message);
        return;
    }

    document.getElementById('sectionName').value = '';
    // Teachers' checkbox lists depend on the up-to-date section list, so
    // load sections first and only then rebuild the teacher table.
    await loadSections();
    await loadTeachers();
}

async function deleteSection(id) {
    if (!confirm('Delete this section?')) return;

    try {
        await apiFetch(`/api/admin/sections/${id}`, { method: 'DELETE' });
    } catch (err) {
        alert(err.message);
        return;
    }

    await loadSections();
    await loadTeachers();
}

async function loadTeachers() {
    const tbody = document.querySelector('#teachersTable tbody');
    tbody.innerHTML = '<tr><td colspan="4" class="empty-note">Loading...</td></tr>';

    let teachers;
    try {
        teachers = await apiFetch('/api/admin/teachers');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="4" class="empty-note">Failed to load teachers: ${err.message}</td></tr>`;
        return;
    }

    tbody.innerHTML = '';
    if (teachers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="empty-note">No teachers yet.</td></tr>';
        return;
    }

    teachers.forEach(t => {
        const tr = document.createElement('tr');
        const checkboxes = allSections.map(s =>
            `<label style="display:inline-block; font-weight:normal; margin-right:8px;">
                <input type="checkbox" value="${s.id}" data-teacher="${t.id}" class="section-check-${t.id}"> ${s.name}
             </label>`
        ).join('');
        tr.innerHTML = `
            <td>${t.username}</td>
            <td>${t.fullName}</td>
            <td>${checkboxes}<br><button class="primary" style="margin-top:6px; padding:4px 10px; font-size:12px;" onclick="assignSections(${t.id})">Save</button></td>
            <td><button class="danger" onclick="deleteTeacher(${t.id})">Delete</button></td>
        `;
        tbody.appendChild(tr);
    });
}

async function createTeacher() {
    const username = document.getElementById('teacherUsername').value.trim();
    const fullName = document.getElementById('teacherFullName').value.trim();
    const password = document.getElementById('teacherPassword').value;
    if (!username || !fullName || !password) return;

    try {
        await apiFetch('/api/admin/teachers', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, fullName, password })
        });
    } catch (err) {
        alert(err.message);
        return;
    }

    document.getElementById('teacherUsername').value = '';
    document.getElementById('teacherFullName').value = '';
    document.getElementById('teacherPassword').value = '';
    await loadTeachers();
}

async function deleteTeacher(id) {
    if (!confirm('Delete this teacher?')) return;

    try {
        await apiFetch(`/api/admin/teachers/${id}`, { method: 'DELETE' });
    } catch (err) {
        alert(err.message);
        return;
    }

    await loadTeachers();
    await loadSections();
}

async function assignSections(teacherId) {
    const checks = document.querySelectorAll(`.section-check-${teacherId}`);
    const sectionIds = Array.from(checks).filter(c => c.checked).map(c => Number(c.value));

    try {
        await apiFetch(`/api/admin/teachers/${teacherId}/sections`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sectionIds })
        });
    } catch (err) {
        alert(err.message);
        return;
    }

    await loadSections();
}

async function loadStudents() {
    const tbody = document.querySelector('#studentsTable tbody');
    tbody.innerHTML = '<tr><td colspan="6" class="empty-note">Loading...</td></tr>';

    let students;
    try {
        students = await apiFetch('/api/admin/students');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="empty-note">Failed to load students: ${err.message}</td></tr>`;
        return;
    }

    tbody.innerHTML = '';
    if (students.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="empty-note">No students yet.</td></tr>';
        return;
    }

    students.forEach(s => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${s.username}</td>
            <td>${s.fullName}</td>
            <td>${s.rollNumber || '-'}</td>
            <td>${s.section || '-'}</td>
            <td>${s.anonId}</td>
            <td><button class="danger" onclick="deleteStudent(${s.id})">Delete</button></td>
        `;
        tbody.appendChild(tr);
    });
}

async function loadPendingStudents() {
    let pending;
    try {
        pending = await apiFetch('/api/admin/students/pending');
    } catch (err) {
        // Non-critical section; fail quietly rather than blocking the rest of the page.
        console.error('Failed to load pending students:', err.message);
        return;
    }

    const card = document.getElementById('pendingCard');
    if (pending.length === 0) {
        card.style.display = 'none';
        return;
    }
    card.style.display = 'block';

    const tbody = document.querySelector('#pendingTable tbody');
    tbody.innerHTML = '';
    pending.forEach(s => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${s.username}</td>
            <td>${s.fullName}</td>
            <td>${s.rollNumber || '-'}</td>
            <td>${s.section || '-'}</td>
            <td>
                <button class="primary" style="padding:5px 12px; font-size:12px; margin-top:0;" onclick="approveStudent(${s.id})">Approve</button>
                <button class="danger" onclick="rejectStudent(${s.id})">Reject</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function approveStudent(id) {
    try {
        await apiFetch(`/api/admin/students/${id}/approve`, { method: 'POST' });
    } catch (err) {
        alert(err.message);
        return;
    }
    await loadPendingStudents();
    await loadStudents();
}

async function rejectStudent(id) {
    if (!confirm('Reject and delete this registration?')) return;
    try {
        await apiFetch(`/api/admin/students/${id}/reject`, { method: 'POST' });
    } catch (err) {
        alert(err.message);
        return;
    }
    await loadPendingStudents();
}

async function createStudent() {
    const username = document.getElementById('studentUsername').value.trim();
    const fullName = document.getElementById('studentFullName').value.trim();
    const password = document.getElementById('studentPassword').value;
    const sectionId = document.getElementById('studentSection').value;
    if (!username || !fullName || !password || !sectionId) return;

    try {
        await apiFetch('/api/admin/students', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, fullName, password, sectionId })
        });
    } catch (err) {
        alert(err.message);
        return;
    }

    document.getElementById('studentUsername').value = '';
    document.getElementById('studentFullName').value = '';
    document.getElementById('studentPassword').value = '';
    await loadStudents();
}

async function deleteStudent(id) {
    if (!confirm('Delete this student?')) return;
    try {
        await apiFetch(`/api/admin/students/${id}`, { method: 'DELETE' });
    } catch (err) {
        alert(err.message);
        return;
    }
    await loadStudents();
}

(async function init() {
    if (!(await guard('ADMIN'))) return;
    await loadSections();
    await loadTeachers();
    await loadStudents();
    await loadPendingStudents();
})();
