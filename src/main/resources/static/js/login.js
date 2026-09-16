async function login() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const errorEl = document.getElementById('error');
    errorEl.textContent = '';

    try {
        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();

        if (!res.ok) {
            errorEl.textContent = data.error || 'Login failed';
            return;
        }

        if (data.role === 'ADMIN') window.location.href = 'admin.html';
        else if (data.role === 'TEACHER') window.location.href = 'teacher.html';
        else if (data.role === 'STUDENT') window.location.href = 'student.html';

    } catch (e) {
        errorEl.textContent = 'Could not reach server';
    }
}

// Allow pressing Enter to submit
document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('password').addEventListener('keydown', e => {
        if (e.key === 'Enter') login();
    });
});
