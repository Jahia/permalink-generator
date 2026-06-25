/**
 * Execute a GraphQL request against the Jahia GraphQL endpoint.
 * @param {string} contextPath - Jahia context path (e.g. '' or '/cms').
 * @param {{ query: string, variables?: object }} body - GraphQL request body.
 * @returns {Promise<object>} Parsed JSON response (may contain a top-level `errors` array).
 */
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

/**
 * POST form-encoded parameters to a Jahia action URL via XHR.
 * XHR is used (rather than fetch) so CsrfGuardJavascriptFilter's XMLHttpRequest
 * patch can auto-inject the CSRF token.
 * @param {string} url - Target action URL.
 * @param {URLSearchParams} params - Form parameters to send.
 * @returns {Promise<object>} Parsed JSON response from the action.
 */
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
