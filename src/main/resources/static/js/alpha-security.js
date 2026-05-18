(function () {
    function cookie(name) {
        const prefix = name + "=";
        return document.cookie.split(";")
            .map(part => part.trim())
            .find(part => part.startsWith(prefix))
            ?.slice(prefix.length) || "";
    }

    function csrfToken() {
        const value = cookie("XSRF-TOKEN");
        return value ? decodeURIComponent(value) : "";
    }

    function csrfHeaderName() {
        return "X-XSRF-TOKEN";
    }

    function isUnsafe(method) {
        const normalized = (method || "GET").toUpperCase();
        return normalized !== "GET" && normalized !== "HEAD" && normalized !== "OPTIONS" && normalized !== "TRACE";
    }

    function isSameOrigin(input) {
        const url = typeof input === "string" ? input : input && input.url;
        if (!url) return true;
        return new URL(url, window.location.href).origin === window.location.origin;
    }

    function ensureErrorHost() {
        let host = document.getElementById("magenta-security-error");
        if (!host) {
            host = document.createElement("div");
            host.id = "magenta-security-error";
            host.setAttribute("role", "alert");
            host.setAttribute("aria-live", "polite");
            host.className = "mag-auth-error";
            document.body.prepend(host);
        }
        return host;
    }

    function onBodyReady(callback) {
        if (document.body) {
            callback();
        } else {
            document.addEventListener("DOMContentLoaded", callback, { once: true });
        }
    }

    onBodyReady(function () {
        document.body.addEventListener("htmx:configRequest", function (event) {
            const method = event.detail && event.detail.verb;
            const token = csrfToken();
            if (token && isUnsafe(method)) {
                event.detail.headers[csrfHeaderName()] = token;
            }
        });

        document.body.addEventListener("htmx:responseError", function (event) {
            const xhr = event.detail && event.detail.xhr;
            if (!xhr || (xhr.status !== 401 && xhr.status !== 403)) return;
            const host = ensureErrorHost();
            host.textContent = xhr.status === 401
                ? "Authentication required."
                : "CSRF token missing or invalid.";
        });
    });

    if (window.fetch) {
        const nativeFetch = window.fetch.bind(window);
        window.fetch = function (input, init) {
            const options = init ? { ...init } : {};
            const method = options.method || (input && input.method) || "GET";
            if (isUnsafe(method) && isSameOrigin(input)) {
                const token = csrfToken();
                if (token) {
                    const headers = new Headers(options.headers || (input && input.headers) || {});
                    headers.set(csrfHeaderName(), token);
                    options.headers = headers;
                }
            }
            return nativeFetch(input, options);
        };
    }
})();
