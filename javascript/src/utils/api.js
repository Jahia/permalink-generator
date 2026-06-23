export function gql(contextPath, body) {
    return fetch(contextPath + '/modules/graphql', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
        credentials: 'include',
        body: JSON.stringify(body)
    }).then(r => {
        if (!r.ok) {
            return Promise.reject(Object.assign(new Error('HTTP ' + r.status), { status: r.status }));
        }
        return r.json();
    });
}

// Use XHR so CsrfGuardJavascriptFilter's XMLHttpRequest patch auto-injects the token.
export function postAction(url, params) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open('POST', url);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        xhr.withCredentials = true;
        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                try { resolve(JSON.parse(xhr.responseText)); } catch (e) { resolve({}); }
            } else {
                reject(Object.assign(new Error('HTTP ' + xhr.status), { status: xhr.status }));
            }
        };
        xhr.onerror = () => reject(new Error('Network error'));
        xhr.send(params.toString());
    });
}
