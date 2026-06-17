export function gql(contextPath, body) {
    return fetch(contextPath + '/modules/graphql', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
        credentials: 'include',
        body: JSON.stringify(body)
    }).then(r => r.json());
}

export function postAction(url, params) {
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest' },
        credentials: 'include',
        body: params.toString()
    }).then(r => {
        if (!r.ok) throw Object.assign(new Error('HTTP ' + r.status), { status: r.status });
        return r.json().catch(() => ({}));
    });
}
