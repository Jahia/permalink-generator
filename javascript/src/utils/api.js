function csrfToken() {
    const fromCfg = window.__PL_CONFIG__ && window.__PL_CONFIG__.csrfToken;
    if (fromCfg) return fromCfg;
    // CSRFGuard 3.x JS API (injected by csrfguard.js)
    if (window.OWASP_CSRFGUARD && typeof window.OWASP_CSRFGUARD.getTokenValue === 'function') {
        return window.OWASP_CSRFGUARD.getTokenValue() || '';
    }
    return '';
}

export function gql(contextPath, body) {
    return fetch(contextPath + '/modules/graphql', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
            'OWASP_CSRFTOKEN': csrfToken()
        },
        credentials: 'include',
        body: JSON.stringify(body)
    }).then(r => r.json());
}

export function postAction(url, params) {
    params.set('OWASP_CSRFTOKEN', csrfToken());
    return fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Requested-With': 'XMLHttpRequest',
            'OWASP_CSRFTOKEN': csrfToken()
        },
        credentials: 'include',
        body: params.toString()
    }).then(r => {
        if (!r.ok) throw Object.assign(new Error('HTTP ' + r.status), { status: r.status });
        return r.json().catch(() => ({}));
    });
}
