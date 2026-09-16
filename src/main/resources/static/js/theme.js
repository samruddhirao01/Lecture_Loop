// Applies saved theme preference and provides the toggle button behavior.
// The actual "apply before paint" happens via an inline script in each
// page's <head> (to avoid a flash of the wrong theme) -- this file just
// wires up the button and keeps localStorage in sync.

function toggleTheme() {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    if (isDark) {
        document.documentElement.removeAttribute('data-theme');
        localStorage.setItem('lr-theme', 'light');
    } else {
        document.documentElement.setAttribute('data-theme', 'dark');
        localStorage.setItem('lr-theme', 'dark');
    }
    updateThemeIcon();
}

function updateThemeIcon() {
    const btn = document.getElementById('themeToggleBtn');
    if (!btn) return;
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    btn.textContent = isDark ? '\u2600' : '\u{1F319}'; // sun / crescent moon
}

document.addEventListener('DOMContentLoaded', updateThemeIcon);
