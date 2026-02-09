let isUnauthorizedHandled = false;

function getAccessToken() {
    return localStorage.getItem("accessToken");
}

function handleUnauthorized() {
    if (isUnauthorizedHandled) return;
    isUnauthorizedHandled = true;

    alert("인증이 만료되었습니다.");
    localStorage.removeItem("accessToken");
    window.location.href = "/login";
}

async function checkAuth() {
    const token = getAccessToken();
    if (!token || token.trim() === "") {
        alert("로그인이 필요합니다.");
        window.location.href = "/login";
        return false;
    }
    return true;
}
