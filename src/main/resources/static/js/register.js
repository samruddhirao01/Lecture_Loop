async function loadSections() {
    const select = document.getElementById('sectionId');
    let sections;
    try {
        sections = await apiFetch('/api/auth/public-sections');
    } catch (err) {
        select.innerHTML = `<option value="">Failed to load sections</option>`;
        return;
    }
    if (sections.length === 0) {
        select.innerHTML = '<option value="">No sections exist yet - ask your admin</option>';
        return;
    }
    select.innerHTML = sections.map(s => `<option value="${s.id}">${s.name}</option>`).join('');
}

async function register() {
    const errorEl = document.getElementById('error');
    const successEl = document.getElementById('success');
    errorEl.textContent = '';
    successEl.textContent = '';

    const body = {
        username: document.getElementById('username').value.trim(),
        fullName: document.getElementById('fullName').value.trim(),
        rollNumber: document.getElementById('rollNumber').value.trim(),
        sectionId: document.getElementById('sectionId').value,
        password: document.getElementById('password').value
    };

    if (!body.username || !body.fullName || !body.sectionId || !body.password) {
        errorEl.textContent = 'Please fill in all fields.';
        return;
    }

    const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    const data = await res.json();

    if (!res.ok) {
        errorEl.textContent = data.error || 'Registration failed';
        return;
    }

    successEl.textContent = data.message;
    document.getElementById('username').value = '';
    document.getElementById('fullName').value = '';
    document.getElementById('rollNumber').value = '';
    document.getElementById('password').value = '';
}

loadSections();
