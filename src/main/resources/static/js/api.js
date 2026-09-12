// Wraps fetch() so every caller gets the same behavior: network failures,
// non-OK responses, and bad JSON all become a normal thrown Error with a
// useful message, instead of being silently treated as valid data.
async function apiFetch(url, options) {
    let res;
    try {
        res = await fetch(url, options);
    } catch (networkErr) {
        throw new Error('Network error - check your connection and try again.');
    }

    let data = null;
    try {
        data = await res.json();
    } catch (parseErr) {
        data = null; // some endpoints (e.g. logout) may return no body
    }

    if (!res.ok) {
        throw new Error((data && data.error) || `Request failed (${res.status})`);
    }
    return data;
}

// Verifies the logged-in user has the expected role, redirecting to login
// if not. Returns true/false so callers can stop initialization immediately
// instead of letting the rest of the page load run after a redirect fires.
async function guard(expectedRole) {
    try {
        const res = await fetch('/api/auth/me');
        if (!res.ok) {
            window.location.href = 'index.html';
            return false;
        }
        const data = await res.json();
        if (data.role !== expectedRole) {
            window.location.href = 'index.html';
            return false;
        }
        return true;
    } catch (e) {
        window.location.href = 'index.html';
        return false;
    }
}
